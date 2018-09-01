package com.jaagro.tms.api.dto.order;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author tony
 */
@Data
@Accessors(chain = true)
public class UpdateOrderItemsDto implements Serializable {

    private Integer id;

    /**
     * 卸货地id
     */
    private Integer unloadId;

    /**
     * 要求卸货时间
     */
    private Date unloadTime;

    /**
     * 货物列表
     */
    private List<CreateOrderGoodsDto> goods;
}
