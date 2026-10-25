Feature: 发布提问
  作为登录用户
  我想要发布问题
  以便于获得解答

  Scenario: 快乐路径 - 成功发布纯文本问题
    Given 我已经登录系统
    When 我输入标题 "如何配置Git分支保护" 和详情 "求指导"
    And 点击发布按钮
    Then 系统提示发布成功
    And 我能在首页看到该问题

  Scenario: 异常路径 - 必填项为空
    Given 我已经登录系统
    When 我不输入标题直接点击发布
    Then 系统提示 "标题不能为空"
    And 问题未被创建

  Scenario: 边界路径 - 标题超长
    Given 我已经登录系统
    When 我输入超过 100 个字的标题
    Then 系统提示 "标题长度不能超过100字"
