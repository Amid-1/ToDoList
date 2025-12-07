package org.example.service;

import org.example.domain.User;
import org.example.service.interfaces.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService implements IUserService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableUser() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS Users");
        jdbcTemplate.execute("CREATE TABLE Users(Id LONG, Email VARCHAR(30), Password VARCHAR(30))");
    }


    @Override
    public int createUser(User user) {
        String sql = "INSERT INTO Users (Id, Email, Password) VALUES (?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                user.getId(),
                user.getEmail(),
                user.getPassword()
        );
    }


    public User getUser(long id) {
        String query = "SELECT * FROM Users WHERE Id=?";
        return jdbcTemplate.queryForObject(
                query,
                new Object[]{id},
                new BeanPropertyRowMapper<>(User.class)
        );
    }


    public int updateUser(User updatedUser, long id) {
        String query = "UPDATE Users SET Email='" + updatedUser.getEmail()
                + "', password='" + updatedUser.getPassword()
                + "' WHERE id=" + id;
        return jdbcTemplate.update(query);
    }


    public int deleteUser(long id) {
        String sql = "DELETE FROM Users WHERE Id = ?";
        return jdbcTemplate.update(sql, id);
    }

}
