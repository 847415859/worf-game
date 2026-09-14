package com.wolf.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wolf.backend.entity.GamePlayer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GamePlayerMapper extends BaseMapper<GamePlayer> {
}
