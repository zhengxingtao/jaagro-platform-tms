package com.jaagro.tms.api.dto.waybill;

import com.jaagro.tms.api.dto.base.ShowUserDto;
import com.jaagro.tms.api.dto.truck.ShowDriverDto;
import com.jaagro.tms.api.dto.truck.ShowTruckDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author tony
 */
@Data
@Accessors(chain = true)
public class ListWaybillDto implements Serializable {
    /**
     * 运单号
     */
    private Integer id;

    /**
     * 订单号
     */
    private Integer orderId;

    /**
     * 卸货地名称
     */
    private List<String> unloadName;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 运单状态：待派车、待司机接单、已接单、司机已出发、司机到达装货地、运输中、卸货完成、已完成，取消
     */
    private String waybillStatus;

    /**
     * 车辆id
     */
    private ShowTruckDto truck;

    /**
     * 司机id
     */
    private ShowDriverDto driver;

    /**
     * 任务推送司机时间
     */
    private Date sendTime;

    /**
     * 实际公里数
     */
    private BigDecimal distance;

    /**
     * 创建时间（派单时间）
     */
    private Date createTime;

    /**
     * 创建人（派单人）
     */
    private ShowUserDto createdUserId;

    /**
     * 磅差超过千分之二 进行预警提醒
     */
    private boolean poundAlert;
    /**
     * 回单补录状态 0-未回单,1-已补录实提,2-已补录实卸
     */
    private Integer receiptStatus;

    /**
     * 货物类型
     */
    private Integer goodsType;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 重量
     */
    private BigDecimal weight;

    /**
     *
     */
    private Integer networkId;
}
