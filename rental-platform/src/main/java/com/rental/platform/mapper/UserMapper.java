package com.rental.platform.mapper;

import com.rental.platform.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE username = #{keyword} OR email = #{keyword} OR phone = #{keyword}")
    User findByAccount(@Param("keyword") String keyword);

    @Select("SELECT * FROM t_user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT * FROM t_user WHERE role != 'ADMIN' ORDER BY id")
    List<User> findAll();

    @Insert("INSERT INTO t_user (username, password, email, phone, role, points, level, status) VALUES (#{username}, #{password}, #{email}, #{phone}, #{role}, #{points}, #{level}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE t_user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE t_user SET points = points + #{points} WHERE id = #{id}")
    int addPoints(@Param("id") Long id, @Param("points") Integer points);

    @Update("UPDATE t_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE t_user SET phone = #{phone} WHERE id = #{id}")
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    @Update("UPDATE t_user SET email = #{email} WHERE id = #{id}")
    int updateEmail(@Param("id") Long id, @Param("email") String email);

    @Update("UPDATE t_user SET avatar = #{avatar} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    @Select("SELECT COUNT(*) FROM t_user")
    int countAll();
}
