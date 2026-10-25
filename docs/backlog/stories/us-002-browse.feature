Feature: 浏览与搜索问题
  作为访客
  我想要浏览和搜索问题列表
  以便于找到我感兴趣的答案

  Scenario: 快乐路径 - 成功浏览问题列表
    Given 我处于网站首页
    When 我不输入任何搜索词
    Then 我应该能看到按时间倒序排列的问题列表

  Scenario: 快乐路径 - 通过关键词搜索
    Given 我处于网站首页
    When 我在搜索框输入 "Java"
    And 点击搜索按钮
    Then 我应该只能看到标题或内容包含 "Java" 的问题
