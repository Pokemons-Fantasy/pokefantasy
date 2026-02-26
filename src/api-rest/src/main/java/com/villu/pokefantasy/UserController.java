package com.villu.pokefantasy;

import com.villu.pokefantasy.commands.users.saveUser.SaveUser;
import com.villu.pokefantasy.request.user.UserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class UserController {

    @Autowired
    private SaveUser saveUser;

    @PostMapping("/user")
    public ResponseEntity<Void> createUser(@RequestBody UserRequest user) {
        saveUser.saveUser(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/login")
    public ResponseEntity<Boolean> loginUser(@RequestBody UserRequest user) {
        return ResponseEntity.ok(saveUser.login(user));
    }
}
