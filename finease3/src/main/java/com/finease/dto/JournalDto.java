package com.finease.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalDto {
    public long id;
    public String date;    // format yyyy-MM-dd
    public String desc;
    public List<LineDto> lines = new ArrayList<>();
}