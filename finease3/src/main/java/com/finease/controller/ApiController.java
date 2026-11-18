package com.finease.controller;

import com.finease.dto.AccountDto;
import com.finease.dto.IdRequest;
import com.finease.dto.JournalDto;
import com.finease.service.FinService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final FinService service;

    public ApiController(FinService service) {
        this.service = service;
    }

    @GetMapping(params = "a=load")
    public Map<String, Object> load() {
        return service.load();
    }

    @PostMapping(params = "a=addAccount")
    public Map<String, Object> addAccount(@RequestBody AccountDto body) {
        service.addAccount(body);
        return Map.of("ok", true);
    }

    @PostMapping(params = "a=saveJournal")
    public Map<String, Object> saveJournal(@RequestBody JournalDto body) {
        long id = service.saveJournal(body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("id", id);
        return out;
    }

    @PostMapping(params = "a=delJournal")
    public Map<String, Object> delJournal(@RequestBody IdRequest body) {
        service.delJournal(body.id);
        return Map.of("ok", true);
    }
}