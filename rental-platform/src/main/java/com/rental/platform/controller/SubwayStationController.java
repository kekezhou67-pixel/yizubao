package com.rental.platform.controller;

import com.rental.platform.entity.SubwayStation;
import com.rental.platform.mapper.SubwayStationMapper;
import com.rental.platform.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subway")
public class SubwayStationController {

    private final SubwayStationMapper subwayStationMapper;

    @Autowired
    public SubwayStationController(SubwayStationMapper subwayStationMapper) {
        this.subwayStationMapper = subwayStationMapper;
    }

    @GetMapping("/cities")
    public Result<List<String>> getCities() {
        List<String> cities = subwayStationMapper.findCities();
        return Result.success(cities);
    }

    @GetMapping("/lines")
    public Result<List<String>> getLines(@RequestParam String city) {
        if (city == null || city.trim().isEmpty()) {
            return Result.error("城市不能为空");
        }
        List<String> lines = subwayStationMapper.findLinesByCity(city);
        return Result.success(lines);
    }

    @GetMapping("/stations")
    public Result<List<SubwayStation>> getStations(@RequestParam String city, @RequestParam("line") String line) {
        if (city == null || city.trim().isEmpty()) {
            return Result.error("城市不能为空");
        }
        if (line == null || line.trim().isEmpty()) {
            return Result.error("线路不能为空");
        }
        List<SubwayStation> stations = subwayStationMapper.findByCityAndLine(city, line);
        return Result.success(stations);
    }
}

