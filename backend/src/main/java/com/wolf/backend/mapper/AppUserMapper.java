package com.wolf.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wolf.backend.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
