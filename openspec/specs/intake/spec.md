# intake Specification

## Purpose

定义归档接收模块的业务边界：外部数据进入正式档案库之前的来源接入、清洗、转换、字段映射、校验和暂存能力。当前阶段只提供系统入口，不对接 NAS、SFTP、HTTP 或其他外部系统。

## Requirements

### Requirement: 归档接收系统入口

系统 SHALL 提供归档接收模块入口，用于承载外部数据接入和归档前处理能力。

#### Scenario: 查询归档接收入口概览

- **WHEN** 前端请求 `GET /api/v1/intake`
- **THEN** 系统 SHALL 返回归档接收入口概览
- **AND** 响应 SHALL 明确表示当前未配置外部连接
- **AND** 响应 SHALL 明确表示当前未对接外部系统

### Requirement: 归档接收与档案核心边界

归档接收 SHALL 负责正式入库前的接入和前处理，档案核心 SHALL 负责已经成为正式档案资产的数据管理。

#### Scenario: 外部数据尚未正式归档

- **WHEN** 外部数据处于来源接入、解析、清洗、映射、校验或暂存阶段
- **THEN** 相关业务能力 SHALL 归入归档接收模块
- **AND** 系统 SHALL NOT 将外部数据清洗、协议接入和暂存处理直接塞入档案记录核心模块

#### Scenario: 数据已经正式成为档案记录

- **WHEN** 数据已经通过归档接收流程并进入正式档案库
- **THEN** 相关管理能力 SHALL 归入档案核心模块
