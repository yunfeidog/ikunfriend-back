package com.yunfei.ikunfriend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunfei.ikunfriend.common.Code;
import com.yunfei.ikunfriend.common.Result;
import com.yunfei.ikunfriend.common.ResultUtils;
import com.yunfei.ikunfriend.exception.BussinessException;
import com.yunfei.ikunfriend.model.domain.Team;
import com.yunfei.ikunfriend.model.domain.User;
import com.yunfei.ikunfriend.model.domain.UserTeam;
import com.yunfei.ikunfriend.model.dto.*;
import com.yunfei.ikunfriend.model.vo.TeamUserVO;
import com.yunfei.ikunfriend.service.TeamService;
import com.yunfei.ikunfriend.service.UserService;
import com.yunfei.ikunfriend.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team")
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedisTemplate redisTemplate;

    @PostMapping("/add")
    public Result<Long> addTeam(@RequestBody TeamAddDTO teamAddDto, HttpServletRequest request) {
        if (teamAddDto == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddDto, team);
        long teamId = teamService.addTeam(team, loginUser);
        if (teamId <= 0) {
            throw new BussinessException(Code.SYSTEM_ERROR, "添加队伍失败");
        }
        return ResultUtils.success(team.getId());
    }

    /**
     * 解散队伍
     *
     * @param deleteDTO
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteTeam(@RequestBody DeleteDTO deleteDTO, HttpServletRequest request) {
        if (deleteDTO == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        Long id = deleteDTO.getId();
        if (id <= 0) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean b = teamService.deleteTeam(id, loginUser);
        if (!b) {
            throw new BussinessException(Code.SYSTEM_ERROR, "删除队伍失败");
        }
        return ResultUtils.success(b);
    }

    @PostMapping("/update")
    public Result<Boolean> updateTeam(@RequestBody TeamUpdateDTO team, HttpServletRequest request) {
        if (team == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean b = teamService.updateTeam(team, loginUser);
        if (!b) {
            throw new BussinessException(Code.SYSTEM_ERROR, "更新队伍失败");
        }
        return ResultUtils.success(b);
    }

    @GetMapping("/get")
    public Result<Team> getTeamById(long id) {
        if (id <= 0) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BussinessException(Code.SYSTEM_ERROR, "获取队伍失败");
        }
        return ResultUtils.success(team);
    }

    /**
     * 获取我创建的队伍
     *
     * @param teamQueryDto
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public Result<List<TeamUserVO>> listMyCreateTeams(TeamQueryDTO teamQueryDto, HttpServletRequest request) {
        if (teamQueryDto == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        teamQueryDto.setUserId(loginUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQueryDto, true);
        return ResultUtils.success(teamList);
    }

    /**
     * 获取我加入的队伍
     *
     * @param teamQueryDto
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public Result<List<TeamUserVO>> listMyJoinTeams(TeamQueryDTO teamQueryDto, HttpServletRequest request) {
        if (teamQueryDto == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
        //取出不重复的队伍id teamId(单)=>userId(多)
        Map<Long, List<UserTeam>> listMap = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQueryDto.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQueryDto, true);
        return ResultUtils.success(teamList);
    }


    @GetMapping("/list")
    public Result<List<TeamUserVO>> listTeams(TeamQueryDTO teamQueryDto, HttpServletRequest request) {
        if (teamQueryDto == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);
        List<TeamUserVO> teamUserVOList = teamService.listTeams(teamQueryDto, isAdmin);
        //判断当前用户是否已经加入队伍
        List<Long> teamIdList = teamUserVOList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            User loginUser = userService.getLoginUser(request);
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            teamUserVOList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception e) {
            throw new BussinessException(Code.SYSTEM_ERROR);
        }
        // 3、查询已加入队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        // 队伍 id => 加入这个队伍的用户列表
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamUserVOList.forEach(team -> team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return ResultUtils.success(teamUserVOList);
    }


    @GetMapping("/list/page")
    public Result<Page<Team>> listTeamsByPage(TeamQueryDTO teamQueryDto) {
        if (teamQueryDto == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        Team team = new Team();
        BeanUtils.copyProperties(teamQueryDto, team);
        Page<Team> page = new Page<>(teamQueryDto.getPageNum(), teamQueryDto.getPageSize());
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> teamPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(teamPage);
    }

    @PostMapping("/join")
    public Result<Boolean> joinTeam(@RequestBody TeamJoinDTO teamJoinDTO, HttpServletRequest request) {
        if (teamJoinDTO == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean b = teamService.joinTeam(teamJoinDTO, loginUser);
        if (!b) {
            throw new BussinessException(Code.SYSTEM_ERROR, "加入队伍失败");
        }
        return ResultUtils.success(b);
    }

    @PostMapping("/quit")
    public Result<Boolean> quitTeam(@RequestBody TeamQuitDTO teamQuitDTO, HttpServletRequest request) {
        if (teamQuitDTO == null) {
            throw new BussinessException(Code.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean b = teamService.quitTeam(teamQuitDTO, loginUser);
        if (!b) {
            throw new BussinessException(Code.SYSTEM_ERROR, "加入队伍失败");
        }
        return ResultUtils.success(b);
    }
}
