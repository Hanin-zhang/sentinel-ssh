package com.zhanghan.sshproxyproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select * from user where username = #{username}")
    User findByName(String username);

    @Update("update user set danger_total_num = danger_total_num + 1 where id = #{userId}")
    boolean updateDangerCmd(Long userId);

    @Select("select id from user where username = #{username}")
    Long findIfHavingUser(String username);

    @Select("select id from user where email = #{email}")
    Long findIfHavingUserByEmail(String email);

    @Select("select username , status from user where email = #{email}")
    LoginFormDTO selectByEmail(String email);
}
