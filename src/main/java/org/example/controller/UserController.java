package org.example.controller;

import org.example.domain.User;
import org.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(path = "/create_table")
    public ResponseEntity<String> getTest(){
        userService.createTableUser();
        return new ResponseEntity<>("this is our test massage", HttpStatus.OK);
    }

    @PostMapping(path = "/user/create")
    public ResponseEntity<Integer> createUser(@RequestBody User user) {
        int result = userService.createUser(user);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @GetMapping("/user/get/{id}")
    public ResponseEntity<User> getUser(@PathVariable("id") long id) {
        User result = userService.getUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PutMapping(path = "/user/update/{id}")
    public ResponseEntity<Integer> updateUser(@RequestBody User updatedUser, @PathVariable("id") long id) {
        int result = userService.updateUser(updatedUser, id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @DeleteMapping(path = "/user/delete/{id}")
    public ResponseEntity<Integer> deleteUser(@PathVariable("id") long id) {
        int result = userService.deleteUser(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}
