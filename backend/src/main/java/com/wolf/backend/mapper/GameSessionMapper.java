package com.wolf.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wolf.backend.entity.GameSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameSessionMapper extends BaseMapper<GameSession> {
}
