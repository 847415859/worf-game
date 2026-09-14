package com.wolf.backend.controller;

import com.wolf.backend.entity.RoleDefinition;
import com.wolf.backend.entity.RoomTemplate;
import com.wolf.backend.mapper.RoleDefinitionMapper;
import com.wolf.backend.mapper.RoomTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 供前端开局界面拉取角色定义/局型模板，不属于文档17节最小契约但同一前缀下的必要补充 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetaController {

    private final RoleDefinitionMapper roleDefinitionMapper;
    private final RoomTemplateMapper roomTemplateMapper;

    @GetMapping("/role-definitions")
    public List<RoleDefinition> roleDefinitions() {
        return roleDefinitionMapper.selectList(null);
    }

    @GetMapping("/room-templates")
    public List<RoomTemplate> roomTemplates() {
        return roomTemplateMapper.selectList(null);
    }
}
