package com.jaagro.tms.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jaagro.constant.UserInfo;
import com.jaagro.tms.api.constant.OrderStatus;
import com.jaagro.tms.api.constant.WaybillStatus;
import com.jaagro.tms.api.dto.base.ListTruckTypeDto;
import com.jaagro.tms.api.dto.customer.ShowCustomerDto;
import com.jaagro.tms.api.dto.customer.ShowSiteDto;
import com.jaagro.tms.api.dto.order.GetOrderDto;
import com.jaagro.tms.api.dto.truck.ShowDriverDto;
import com.jaagro.tms.api.dto.truck.ShowTruckDto;
import com.jaagro.tms.api.dto.truck.TruckDto;
import com.jaagro.tms.api.dto.waybill.*;
import com.jaagro.tms.api.service.OrderService;
import com.jaagro.tms.api.service.WaybillService;
import com.jaagro.tms.biz.entity.*;
import com.jaagro.tms.biz.mapper.*;
import com.jaagro.tms.biz.service.CustomerClientService;
import com.jaagro.tms.biz.service.DriverClientService;
import com.jaagro.tms.biz.service.TruckClientService;
import com.jaagro.tms.biz.service.TruckTypeClientService;
import com.jaagro.tms.biz.vo.MiddleObjectVo;
import com.jaagro.utils.ServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author tony
 */
@Service
public class WaybillServiceImpl implements WayBillService {
    private static final Logger log = LoggerFactory.getLogger(WaybillServiceImpl.class);

    @Autowired
    private CurrentUserService currentUserService;
    @Autowired
    private TruckTypeClientService truckTypeClientService;
    @Autowired
    private CustomerClientService customerClientService;
    @Autowired
    private WaybillMapper waybillMapper;
    @Autowired
    private WaybillItemsMapper waybillItemsMapper;
    @Autowired
    private WaybillGoodsMapper waybillGoodsMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private OrderItemsMapper orderItemsMapper;
    @Autowired
    private OrderGoodsMapper orderGoodsMapper;
    @Autowired
    private WaybillTrackingImagesMapper waybillTrackingImagesMapper;
    @Autowired
    private WaybillTrackingMapper waybillTrackingMapper;
    @Autowired
    private OrderGoodsMarginMapper orderGoodsMarginMapper;

    @Override
    public List<ListWaybillPlanDto> createWaybillPlan(CreateWaybillPlanDto waybillDto){
        Integer orderId = waybillDto.getOrderId();
        List<CreateWaybillItemsPlanDto>  waybillItemsDtos = waybillDto.getWaybillItems();
        List<TruckDto> truckDtos = waybillDto.getTrucks();
        List<MiddleObjectVo> middleObjects = new ArrayList<>();
        for(CreateWaybillItemsPlanDto waybillItemsDto:waybillItemsDtos) {
            Integer orderItemId = waybillItemsDto.getOrderItemId();
            List<CreateWaybillGoodsPlanDto> goods =  waybillItemsDto.getGoods();
            for(CreateWaybillGoodsPlanDto waybillGoodsDto:goods){
                MiddleObjectVo  middleObject = new MiddleObjectVo();
                middleObject.setOrderId(orderId);
                middleObject.setOrderItemId(orderItemId);
                middleObject.setOrderGoodsId(waybillGoodsDto.getGoodsId());
                middleObject.setProportioning(waybillGoodsDto.getProportioning());
                middleObjects.add(middleObject);
            }
        }
        List<ListWaybillPlanDto> waybillDtos = null;
        if(!CollectionUtils.isEmpty(middleObjects)){
            try {
                waybillDtos=wayBillAlgorithm(middleObjects,truckDtos);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("计算配运单失败，货物内容={}", middleObjects);
                throw new RuntimeException("计算配运单失败");
            }
        }
        return waybillDtos;
    }

    /**
     * 根据orderId获取订单计划
     *
     * @param orderId
     * @return
     */
    @Override
    public Map<String, Object> getWaybillPlanByOrderId(Integer orderId) {
        return null;
    }

    /**
     * 从配载计划中移除运单【逻辑删除】
     *
     * @param waybillId
     * @return 结果集
     * @author tony
     */
    @Override
    public Map<String, Object> removeWaybillFromPlan(Integer waybillId) {
        //判断运单状态是否满足条件
        return null;
    }

    /**
     * 货物配运单核心算法
     * @param middleObjects
     * @param truckDtos
     */
    private List<ListWaybillPlanDto> wayBillAlgorithm(List<MiddleObjectVo> middleObjects,List<TruckDto> truckDtos){
        List<MiddleObjectVo> middleObjects_assigned = new ArrayList<>();
        List<ListWaybillPlanDto> waybillDtos = new ArrayList<>();
        //按配送地排序
        List<MiddleObjectVo> newMiddleObjectsList = middleObjects.stream().sorted((e1,e2)->Integer.compare(e1.getOrderItemId(),e2.getOrderItemId())).collect(Collectors.toList());
        Iterator<TruckDto> truckIterator = truckDtos.iterator();
        while(truckIterator.hasNext()) {
            TruckDto truckDto = truckIterator.next();
            Integer capacity = truckDto.getCapacity();
            //使用按照送货地排序后货物分配到车辆生成临时送货单
            List<MiddleObjectVo> assignList = new ArrayList<>();
            for (int k = 0;k<truckDto.getNumber(); k++){
                Iterator<MiddleObjectVo> goodsIterator = newMiddleObjectsList.iterator();
                Integer proposioningCount = 0;
                while (goodsIterator.hasNext()) {
                    MiddleObjectVo middleObject = goodsIterator.next();
                    proposioningCount += middleObject.getProportioning();
                    Integer planAmount  = (middleObject.getProportioning() + capacity - proposioningCount);
                    MiddleObjectVo assignedObj = new MiddleObjectVo();
                    BeanUtils.copyProperties(middleObject,assignedObj);
                    assignList.add(assignedObj);
                    if (proposioningCount > capacity) {
                        List<MiddleObjectVo> assignedDtos = assignList.stream().filter(c -> c.getOrderGoodsId().equals(middleObject.getOrderGoodsId())).collect(Collectors.toList());
                        assignedDtos.get(0).setTruckId(truckDto.getTruckId());
                        assignedDtos.get(0).setProportioning(planAmount);
                        assignedDtos.get(0).setPlanAmount(planAmount);
                        assignedDtos.get(0).setUnPlanAmount(0);
                        middleObject.setProportioning(proposioningCount - capacity);
                    } else {
                        List<MiddleObjectVo> assignedDtos = assignList.stream().filter(c -> c.getOrderGoodsId().equals(middleObject.getOrderGoodsId())).collect(Collectors.toList());
                        assignedDtos.get(0).setTruckId(truckDto.getTruckId());
                        assignedDtos.get(0).setPlanAmount(middleObject.getProportioning());
                        assignedDtos.get(0).setUnPlanAmount(0);
                        goodsIterator.remove();
                    }

                    Integer assignTotal = assignList.stream().mapToInt(c -> c.getPlanAmount()).sum();
                    boolean flag = (assignTotal.equals(capacity) || (assignTotal<capacity && newMiddleObjectsList.size()==0));
                    if (flag) {
                        middleObjects_assigned.addAll(assignList);
                        ListWaybillPlanDto waybillDto = this.makeOneWaybillPlan(assignList);
                        waybillDtos.add(waybillDto);
                        assignList.clear();
                        break;
                    }
                }
            }
        }
        System.out.println("margin==="+newMiddleObjectsList.toString());
        System.out.println("assigned==="+middleObjects_assigned.toString());
        log.error("货物配运时剩余货物，margin={}", newMiddleObjectsList);
        log.error("货物配运时已经分配的货物，assigned={}", middleObjects_assigned);

        return waybillDtos;
    }

    /**
     * 生成一个运单计划
     * @param assignList
     * @return
     */
    private  ListWaybillPlanDto makeOneWaybillPlan(List<MiddleObjectVo> assignList){
        ListWaybillPlanDto waybillPlanDto = new ListWaybillPlanDto();
        Orders ordersData = null;
        ListTruckTypeDto truckType = null;
        Integer orderId = 0;
        Integer truckTypeId = 0;
        for (MiddleObjectVo obj:assignList) {
            orderId = obj.getOrderId();
            ordersData = ordersMapper.selectByPrimaryKey(orderId);
            if(ordersData == null){
                throw new NullPointerException("订单号为：" + orderId + " 不存在");
            }
            truckTypeId= obj.getTruckId();
            truckType = truckTypeClientService.getTruckTypeById(truckTypeId);
            break;
        }
        waybillPlanDto.setOrderId(orderId);
        waybillPlanDto.setNeedTruckTypeId(truckTypeId);
        waybillPlanDto.setNeedTruckType(truckType);
        waybillPlanDto.setLoadSiteId(ordersData.getLoadSiteId());
        waybillPlanDto.setTruckTeamContractId(ordersData.getCustomerContractId());
        waybillPlanDto.setLoadTime(ordersData.getLoadTime());
        waybillPlanDto.setWaybillPlanTime(new Date());
        waybillPlanDto.setGoodType(ordersData.getGoodsType());
        List<ListWaybillItemsPlanDto> itemsList = new ArrayList<>();
        for (MiddleObjectVo obj:assignList) {
            OrderGoods orderGoods = orderGoodsMapper.selectByPrimaryKey(obj.getOrderGoodsId());
            Integer goodsUnit = orderGoods.getGoodsUnit();
            if(orderGoods == null){
                throw new NullPointerException("goodsId为：" + obj.getOrderGoodsId()+ " 的货物不存在");
            }
            OrderItems orderItems = orderItemsMapper.selectByPrimaryKey(obj.getOrderItemId());
            ListWaybillItemsPlanDto waybillItemsPlanDto = new ListWaybillItemsPlanDto();
            ShowSiteDto showSiteDto = customerClientService.getShowSiteById(orderItems.getUnloadId());
            waybillItemsPlanDto.setUnloadSiteId(orderItems.getUnloadId());
            waybillItemsPlanDto.setOrderItemId(orderItems.getId());
            waybillItemsPlanDto.setShowSiteDto(showSiteDto);
            waybillItemsPlanDto.setRequiredTime(orderItems.getUnloadTime());
            List<ListWaybillGoodsPlanDto> goodsList = new LinkedList<>();
            ListWaybillGoodsPlanDto waybillGoodsDto = new ListWaybillGoodsPlanDto();
            waybillGoodsDto
                    .setOrderGoodsId(orderGoods.getId())
                    .setGoodsName(orderGoods.getGoodsName())
                    .setGoodsQuantity(orderGoods.getGoodsQuantity())
                    .setGoodsUnit(orderGoods.getGoodsUnit())
                    .setJoinDrug(orderGoods.getJoinDrug());
            if(goodsUnit==3){
                waybillGoodsDto.setGoodsWeight(new BigDecimal(obj.getPlanAmount()));
            }else{
                waybillGoodsDto.setGoodsQuantity(obj.getPlanAmount());
            }
            goodsList.add(waybillGoodsDto);
            waybillItemsPlanDto.setGoods(goodsList);
            itemsList.add(waybillItemsPlanDto);
        }
        waybillPlanDto.setWaybillItems(itemsList);
        return waybillPlanDto;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> createWaybill(List<CreateWaybillDto> waybillDtoList) {
        //更新orders表的状态OrderStatus.STOWAGE
        for (CreateWaybillDto createWaybillDto : waybillDtoList) {
            Integer orderId = createWaybillDto.getOrderId();
            Orders orders = new Orders();
            orders.setId(orderId);
            orders.setOrderStatus(OrderStatus.STOWAGE);
            orders.setModifyTime(new Date());
            orders.setModifyUserId(99999999);
            ordersMapper.updateByPrimaryKeySelective(orders);
            break;
        }
        for (CreateWaybillDto createWaybillDto : waybillDtoList) {
            Integer orderId = createWaybillDto.getOrderId();
            Waybill waybill = new Waybill();
            waybill.setOrderId(orderId);
            waybill.setLoadSiteId(createWaybillDto.getLoadSiteId());
            waybill.setNeedTruckType(createWaybillDto.getNeedTruckTypeId());
            waybill.setTruckTeamContractId(createWaybillDto.getTruckTeamContractId());
            waybill.setWaybillStatus(WaybillStatus.RECEIVE);
            waybill.setCreateTime(new Date());
            waybill.setCreatedUserId(99999999);
            //waybill.setCreatedUserId(currentUserService.getCurrentUser().getId());
            waybillMapper.insertSelective(waybill);
            int waybillId = waybill.getId();
            List<CreateWaybillItemsDto> waybillItemsList = createWaybillDto.getWaybillItems();
            for (CreateWaybillItemsDto waybillItemsDto : waybillItemsList) {
                WaybillItems  waybillItem = new WaybillItems();
                waybillItem.setWaybillId(waybillId);
                waybillItem.setUnloadSiteId(waybillItemsDto.getUnloadSiteId());
                waybillItem.setRequiredTime(waybillItemsDto.getRequiredTime());
                waybillItem.setModifyUserId(99999999);
                waybillItemsMapper.insertSelective(waybillItem);
                int waybillItemsId =waybillItem.getId();

                List<CreateWaybillGoodsDto> createWaybillGoodsDtoList =  waybillItemsDto.getGoods();
                for (CreateWaybillGoodsDto createWaybillGoodsDto : createWaybillGoodsDtoList) {
                    WaybillGoods waybillGoods = new WaybillGoods();
                    waybillGoods.setWaybillItemId(waybillItemsId);
                    waybillGoods.setOrderGoodsId(createWaybillGoodsDto.getOrderGoodsId());
                    waybillGoods.setGoodsName(createWaybillGoodsDto.getGoodsName());
                    waybillGoods.setGoodsUnit(createWaybillGoodsDto.getGoodsUnit());
                    if(createWaybillGoodsDto.getGoodsUnit()==3){
                        waybillGoods.setGoodsWeight(createWaybillGoodsDto.getGoodsWeight());
                    }else{
                        waybillGoods.setGoodsQuantity(createWaybillGoodsDto.getGoodsQuantity());
                    }
                    waybillGoods.setJoinDrug(createWaybillGoodsDto.getJoinDrug());
                    waybillGoods.setModifyUserId(99999999);
                    waybillGoodsMapper.insertSelective(waybillGoods);
                    //插入order_goods_margin
                    OrderGoodsMargin orderGoodsMargin = new OrderGoodsMargin();
                    orderGoodsMargin.setOrderId(orderId);
                    orderGoodsMargin.setOrderItemId(waybillItemsDto.getOrderItemId());
                    orderGoodsMargin.setOrderGoodsId(createWaybillGoodsDto.getOrderGoodsId());
                    orderGoodsMargin.setMargin(BigDecimal.ZERO);
                    orderGoodsMarginMapper.insertSelective(orderGoodsMargin);
                }
            }

        }
        return ServiceResult.toResult("运单创建成功");
    }

    /**
     * 根据订单号获取运单列表
     *
     * @param orderId
     * @return
     */
    @Override
    public List<GetWaybillDto> listWaybillByOrderId(Integer orderId) {
        List<Waybill> waybillList = waybillMapper.selectByOrderId(orderId);
        if (waybillList == null) {
            throw new NullPointerException("当前订单无有效运单");
        }
        List<GetWaybillDto> getWaybills = new ArrayList<>();
        for (Waybill waybill : waybillList) {
            GetWaybillDto getWaybillDto = new GetWaybillDto();
            BeanUtils.copyProperties(waybill, getWaybillDto);
            getWaybills.add(getWaybillDto);
        }

        return null;
    }

    /**
     * 根据id获取waybill对象
     *
     * @param id
     * @return
     */
    @Override
    public GetWaybillDto getWaybillById(Integer id) {
        //拿到waybill对象
        Waybill waybill = waybillMapper.selectByPrimaryKey(id);
        if(null == waybill){
            throw new NullPointerException(id + ": 无效");
        }
        //拿到装货地对象
        ShowSiteDto loadSiteDto = customerClientService.getShowSiteById(waybill.getLoadSiteId());

        //拿到车型对象
        ListTruckTypeDto truckTypeDto = null;
        if (!StringUtils.isEmpty(waybill.getNeedTruckType())) {
            truckTypeDto = truckTypeClientService.getTruckTypeById(waybill.getNeedTruckType());
        }

        ShowDriverDto showDriverDto = null;
        if(!StringUtils.isEmpty(waybill.getDriverId())){
            showDriverDto = driverClientService.getDriverReturnObject(waybill.getDriverId());
        }
        //车辆对象
        ShowTruckDto truckDto = null;
        if (!StringUtils.isEmpty(waybill.getTruckId())) {
            truckDto = truckClientService.getTruckByIdReturnObject(waybill.getTruckId());
        }
        //获取waybillItem列表
        List<GetWaybillItemsDto> getWaybillItemsDtoList = new ArrayList<>();
        List<WaybillItems> waybillItemsList = waybillItemsMapper.listWaybillItemsByWaybillId(waybill.getId());
        for (WaybillItems items : waybillItemsList) {
            GetWaybillItemsDto getWaybillItemsDto = new GetWaybillItemsDto();
            BeanUtils.copyProperties(items, getWaybillItemsDto);
            List<ShowGoodsDto> showGoodsDtoList = new LinkedList<>();
            List<WaybillGoods> waybillGoodsList = waybillGoodsMapper.listWaybillGoodsByItemId(items.getId());
            for (WaybillGoods wg : waybillGoodsList) {
                ShowGoodsDto showGoodsDto = new ShowGoodsDto();
                BeanUtils.copyProperties(wg, showGoodsDto);
                showGoodsDtoList.add(showGoodsDto);
            }
            //拿到卸货信息
            ShowSiteDto unloadSite = customerClientService.getShowSiteById(items.getUnloadSiteId());
            getWaybillItemsDto
                    .setUnloadSite(unloadSite)
                    .setGoods(showGoodsDtoList);
            getWaybillItemsDtoList.add(getWaybillItemsDto);
        }
        GetWaybillDto getWaybillDto = new GetWaybillDto();
        BeanUtils.copyProperties(waybill, getWaybillDto);
        getWaybillDto
                .setLoadSite(loadSiteDto)
                .setNeedTruckType(truckTypeDto)
                .setTruckId(truckDto)
                .setDriverId(showDriverDto)
                .setWaybillItems(getWaybillItemsDtoList);

        return getWaybillDto;
    }

    /**
     * 根据orderId获取order和waybill信息
     * 主要用于既需要order也需要waybill的场景
     *
     * 由于采用循环的方式获取值，项目紧任务中，后期时间宽裕需要重构
     * @param orderId
     * @return
     */
    @Override
    public GetWaybillPlanDto getOrderAndWaybill(Integer orderId) {
        GetOrderDto getOrderDto = orderService.getOrderById(orderId);
        if(null == getOrderDto){
            throw new NullPointerException(orderId + " :无效");
        }
        List<Integer> waybillIds = waybillMapper.listWaybillIdByOrderId(orderId);
        if(null == waybillIds){
            throw new NullPointerException(orderId + " :当前订单无有效运单");
        }
        List<GetWaybillDto> getWaybillDtoList = new ArrayList<>(12);
        for(Integer waybillId : waybillIds){
            GetWaybillDto getWaybillDto = this.getWaybillById(waybillId);
            getWaybillDtoList.add(getWaybillDto);
        }
        GetWaybillPlanDto getWaybillPlanDto = new GetWaybillPlanDto();
        getWaybillPlanDto
                .setOrderDto(getOrderDto)
                .setWaybillDtoList(getWaybillDtoList);
        return getWaybillPlanDto;
    }

    /**
     * 根据状态查询我的运单信息
     *
     * @param dto
     * @return
     */
    @Override
    public Map<String, Object> listWaybillByStatus(GetWaybillParamDto dto) {

        UserInfo currentUser = currentUserService.getCurrentUser();
        Waybill waybill = new Waybill();
        waybill.setDriverId(currentUser.getId());
        List<ListWaybillAppDto> listWaybillAppDtos;
        //承运中订单
        if (WaybillStatus.CARRIER.equals(dto.getWaybillStatus())) {
            PageHelper.startPage(dto.getPageNum(), dto.getPageSize());
            List<GetWaybillAppDto> waybillDtos = waybillMapper.selectWaybillByCarrierStatus(waybill);
            listWaybillAppDtos = listWaybill(waybillDtos);
            return ServiceResult.toResult(new PageInfo<>(listWaybillAppDtos));
        }
        //已完成运单
        if (WaybillStatus.ACCOMPLISH.equals(dto.getWaybillStatus())) {
            PageHelper.startPage(dto.getPageNum(), dto.getPageSize());
            waybill.setWaybillStatus(WaybillStatus.ACCOMPLISH);
            List<GetWaybillAppDto> waybillDtos = waybillMapper.selectWaybillByStatus(waybill);
            listWaybillAppDtos = listWaybill(waybillDtos);
            return ServiceResult.toResult(new PageInfo<>(listWaybillAppDtos));
        }
        //取消运单
        if (WaybillStatus.CANCEL.equals(dto.getWaybillStatus())) {
            PageHelper.startPage(dto.getPageNum(), dto.getPageSize());
            waybill.setWaybillStatus(WaybillStatus.CANCEL);
            List<GetWaybillAppDto> waybillDtos = waybillMapper.selectWaybillByStatus(waybill);
            listWaybillAppDtos = listWaybill(waybillDtos);
            return ServiceResult.toResult(new PageInfo<>(listWaybillAppDtos));
        }
        return ServiceResult.error("没有相关运单");
    }

    /**
     * 运单详情页
     *
     * @param waybillId
     * @return
     */
    @Override
    public Map<String, Object> listWayBillDetails(Integer waybillId) {
        GetWaybillDetailsAppDto waybillDetailsAppDto = new GetWaybillDetailsAppDto();
        WaybillTrackingImages waybillTrackingImages = new WaybillTrackingImages();
        Waybill waybillParam = new Waybill();
        waybillParam.setId(waybillId);
        List<GetWaybillAppDto> waybillAppDtos = waybillMapper.selectWaybillByStatus(waybillParam);
        //运单状态
        waybillDetailsAppDto.setWaybillStatus(waybillAppDtos.get(0).getWaybillStatus());
        //运单号
        waybillDetailsAppDto.setWaybillId(waybillId);
        //客户信息
        Orders orders = ordersMapper.selectByPrimaryKey(waybillAppDtos.get(0).getOrderId());
        if (null != orders) {
            ShowCustomerDto showCustomerDto = customerClientService.getShowCustomerById(orders.getCustomerId());
            waybillDetailsAppDto.setCustomer(showCustomerDto);
        }
        //装货信息
        if (null != orders) {
            ShowSiteDto loadSite = customerClientService.getShowSiteById(orders.getLoadSiteId());
            waybillDetailsAppDto.setLoadSite(loadSite);
            //提货单
            waybillTrackingImages.setWaybillId(waybillId);
            waybillTrackingImages.setSiteId(orders.getLoadSiteId());
            List<GetWaybillTrackingImagesDto> loadSiteWaybillTrackingImages = waybillTrackingImagesMapper.listWaybillTrackingImage(waybillTrackingImages);
            loadSite.setWaybillTrackingImagesDtos(loadSiteWaybillTrackingImages);
        }
        //卸货信息
        List<GetWaybillItemsDto> waybillItems = waybillAppDtos.get(0).getWaybillItems();
        List<ShowSiteDto> unloadSiteList = new ArrayList<>();
        for (GetWaybillItemsDto waybillItem : waybillItems) {
            List<ShowGoodsDto> goods = waybillItem.getGoods();
            ShowSiteDto unloadSite = customerClientService.getShowSiteById(waybillItem.getUnloadSiteId());
            unloadSite.setGoods(goods);
            unloadSite.setRequiredTime(waybillItem.getRequiredTime());
            //卸货单
            waybillTrackingImages.setWaybillId(waybillId);
            waybillTrackingImages.setSiteId(waybillItem.getUnloadSiteId());
            List<GetWaybillTrackingImagesDto> waybillTrackingImagesDtosList = waybillTrackingImagesMapper.listWaybillTrackingImage(waybillTrackingImages);
            unloadSite.setWaybillTrackingImagesDtos(waybillTrackingImagesDtosList);
            unloadSiteList.add(unloadSite);

        }
        waybillDetailsAppDto.setUnloadSite(unloadSiteList);

        return ServiceResult.toResult(waybillDetailsAppDto);
    }

    /**
     * 运单轨迹
     *
     * @param waybillId
     * @return
     */
    @Override
    public Map<String, Object> showWaybill(Integer waybillId) {
        WaybillTrackingImages waybillTrackingImages = new WaybillTrackingImages();
        waybillTrackingImages.setWaybillId(waybillId);
        return ServiceResult.toResult(waybillTrackingImagesMapper.listWaybillTrackingImage(waybillTrackingImages));
    }

    /**
     * 接单详情列表
     *
     * @return
     */
    @Override
    public Map<String, Object> receiptList(GetReceiptParamDto dto) {
        PageHelper.startPage(dto.getPageNum(), dto.getPageSize());
        UserInfo currentUser = currentUserService.getCurrentUser();
        Waybill waybill = new Waybill();
        List<GetReceiptListAppDto> receiptList = new ArrayList<>();
        waybill
                .setWaybillStatus(WaybillStatus.RECEIVE)
                .setDriverId(currentUser.getId());
        List<GetWaybillAppDto> waybillAppDtos = waybillMapper.selectWaybillByStatus(waybill);
        for (GetWaybillAppDto waybillAppDto : waybillAppDtos) {
            GetReceiptListAppDto receiptListAppDto = new GetReceiptListAppDto();
            receiptListAppDto.setWaybillId(waybillAppDto.getId());
            List<ShowGoodsDto> goodsList = new ArrayList<>();
            List<ShowSiteDto> unloadSite = new ArrayList<>();
            Orders orders = ordersMapper.selectByPrimaryKey(waybillAppDto.getOrderId());
            if (null != orders) {
                //要求提货时间
                receiptListAppDto.setLoadTime(orders.getLoadTime());
                //装货地
                ShowSiteDto loadSite = customerClientService.getShowSiteById(orders.getLoadSiteId());
                receiptListAppDto.setLoadSite(loadSite);
            }

            List<GetWaybillItemsDto> waybillItems = waybillAppDto.getWaybillItems();
            for (GetWaybillItemsDto waybillItem : waybillItems) {
                //卸货地
                ShowSiteDto unLoadSite = customerClientService.getShowSiteById(waybillItem.getUnloadSiteId());
                unloadSite.add(unLoadSite);
                //货物信息
                List<ShowGoodsDto> goods = waybillItem.getGoods();
                for (ShowGoodsDto good : goods) {
                    goodsList.add(good);
                }
            }
            receiptListAppDto.setUnloadSite(unloadSite);
            receiptListAppDto.setGoods(goodsList);
            receiptList.add(receiptListAppDto);
        }
        return ServiceResult.toResult(new PageInfo<>(receiptList));
    }

    /**
     * 接单状态控制
     *
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upDateReceiptStatus(GetReceiptParamDto dto) {

        boolean isReceipt = false;
        Integer waybillId = dto.getWaybillId();
        UserInfo currentUser = currentUserService.getCurrentUser();
        Waybill waybill = waybillMapper.selectByPrimaryKey(waybillId);
        WaybillTracking waybillTracking = new WaybillTracking();
        waybillTracking
                .setWaybillId(waybillId)
                .setCreateTime(new Date())
                .setDriverId(currentUser.getId())
                .setDevice(dto.getDevice())
                .setTrackingInfo(dto.getTrackingInfo())
                .setLatitude(dto.getLatitude())
                .setLatitude(dto.getLongitude());
        //拒单
        if (WaybillStatus.REJECT.equals(dto.getReceiptStatus())) {
            waybill.setWaybillStatus(WaybillStatus.REJECT);
            waybillMapper.updateByPrimaryKey(waybill);
            waybillTracking
                    .setOldStatus(WaybillStatus.RECEIVE)
                    .setNewStatus(WaybillStatus.REJECT);
            waybillTrackingMapper.insertSelective(waybillTracking);
            return ServiceResult.toResult(isReceipt);
            //接单
        } else if (WaybillStatus.RECEIPT.equals(dto.getReceiptStatus())) {
            isReceipt = true;
            waybill.setId(waybillId);
            waybill.setWaybillStatus(WaybillStatus.DEPART);
            waybillMapper.updateByPrimaryKey(waybill);
            waybillTracking.setOldStatus(WaybillStatus.RECEIVE);
            waybillTracking.setNewStatus(WaybillStatus.DEPART);
            waybillTrackingMapper.insertSelective(waybillTracking);
            return ServiceResult.toResult(isReceipt);
        }
        return ServiceResult.toResult("接单操作失败");
    }


    /**
     * 接单消息列表显示
     *
     * @return
     */
    @Override
    public Map<String, Object> receiptMessage(GetReceiptParamDto dto) {
        return null;
    }

    private List<ListWaybillAppDto> listWaybill(List<GetWaybillAppDto> waybillDtos) {
        List<ListWaybillAppDto> listWaybillAppDtos = new ArrayList<>();
        if (null != waybillDtos && waybillDtos.size() > 0) {
            for (GetWaybillAppDto waybillDto : waybillDtos) {
                ListWaybillAppDto listWaybillAppDto = new ListWaybillAppDto();
                List<ShowGoodsDto> showGoodsDtos = new ArrayList<>();
                List<ShowSiteDto> unloadSiteList = new ArrayList<>();
                //运单号
                listWaybillAppDto.setWaybillId(waybillDto.getId());
                //运单状态
                listWaybillAppDto.setWaybillStatus(waybillDto.getWaybillStatus());
                //接单时间
                WaybillTracking waybillTrackingCondition = new WaybillTracking();
                waybillTrackingCondition
                        .setWaybillId(waybillDto.getId())
                        .setNewStatus(WaybillStatus.DEPART);
                WaybillTracking waybillTracking = waybillTrackingMapper.selectSingleTime(waybillTrackingCondition);
                if (null != waybillTracking) {
                    listWaybillAppDto.setSingleTime(waybillTracking.getCreateTime());
                }
                //客户信息
                Orders orders = ordersMapper.selectByPrimaryKey(waybillDto.getOrderId());
                if (null != orders) {
                    ShowCustomerDto showCustomerDto = customerClientService.getShowCustomerById(orders.getCustomerId());
                    listWaybillAppDto.setCustomer(showCustomerDto);
                }
                //货物信息
                List<GetWaybillItemsDto> waybillItems = waybillDto.getWaybillItems();
                if (null != waybillItems && waybillItems.size() > 0) {
                    for (GetWaybillItemsDto waybillItem : waybillItems) {
                        if (null != waybillItem) {
                            ShowSiteDto unloadSite = customerClientService.getShowSiteById(waybillItem.getUnloadSiteId());
                            unloadSiteList.add(unloadSite);
                        }
                        List<ShowGoodsDto> goods = waybillItem.getGoods();
                        for (ShowGoodsDto good : goods) {
                            showGoodsDtos.add(good);
                        }
                    }
                }
                listWaybillAppDto.setGoods(showGoodsDtos);
                //装货地
                if (null != orders) {
                    ShowSiteDto loadSite = customerClientService.getShowSiteById(orders.getLoadSiteId());
                    listWaybillAppDto.setLoadSite(loadSite);
                }
                //卸货地
                listWaybillAppDto.setUnloadSite(unloadSiteList);
                listWaybillAppDtos.add(listWaybillAppDto);
            }
        }
        return listWaybillAppDtos;
    }
}