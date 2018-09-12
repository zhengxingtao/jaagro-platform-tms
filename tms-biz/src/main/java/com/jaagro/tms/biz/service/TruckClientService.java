package com.jaagro.tms.biz.service;

import com.jaagro.tms.api.dto.truck.ShowTruckDto;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author tony
 */
@FeignClient("crm")
public interface TruckClientService {

    /**
     * 获取车辆对象
     * @param truckId
     * @return
     */
    @GetMapping("/truckToFeign/{truckId}")
    ShowTruckDto getTruckByIdReturnObject(@PathVariable("truckId") Integer truckId);
}
