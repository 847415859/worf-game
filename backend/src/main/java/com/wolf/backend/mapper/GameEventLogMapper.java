package com.wolf.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wolf.backend.entity.GameEventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameEventLogMapper extends BaseMapper<GameEventLog> {
}
