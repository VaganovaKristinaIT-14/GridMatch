package com.ox.tictactoe.server;

import com.ox.tictactoe.server.model.User;
import com.ox.tictactoe.server.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerLauncher {

    public static void main(String[] args) {
        SpringApplication.run(ServerLauncher.class, args);
    }

}
