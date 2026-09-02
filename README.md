<div align="center">

# WeSDAU 课程表

### 面向山东农业大学学生的一站式校园助手

课程表 · 考试安排 · 成绩查询 · 空教室 · 课程提醒

`Android 8.0+` · `LiquidGlass UI` · `浅色 / 深色模式` · `个人 / 全校课表`

<br />

<img src="docs/images/screenshot-schedule-light.webp" alt="WeSDAU 课程表主界面" width="42%" />

<sub>简洁课表视图、LiquidGlass 组件与个性化背景</sub>

</div>

## 关于 WeSDAU

WeSDAU 课程表是一款面向山东农业大学学生的 Android 校园工具。登录教务系统后，可以集中查看个人课表、全校课表、考试安排、成绩和空教室，并支持课程提醒、桌面小组件以及课表和成绩导出。

应用支持本地缓存和离线查看，界面采用 LiquidGlass 液态玻璃风格，并针对浅色、深色和自定义背景提供统一的视觉适配。

## 核心功能

| 功能 | 说明 |
| --- | --- |
| 个人课表 | 按周查看课程，显示课程名称、教师、地点、节次和周数，支持左右滑动切换周次。 |
| 全校课表 | 按学院、年级、专业和班级筛选并查看任意班级课表。 |
| 考试安排 | 查看考试科目、时间、地点等安排信息。 |
| 成绩查询 | 查看课程成绩、学分、绩点、平均成绩、平均绩点和总学分，并支持成绩单导出。 |
| 空教室查询 | 按校区、周次、星期和节次查询可用教室。 |
| 课程提醒 | 在下一节课开始前发送通知，并支持开启或关闭提醒。 |
| 课程编辑 | 添加、编辑和删除自定义课程。 |
| 导出与分享 | 将课表、班级课表或成绩单保存为图片并分享。 |
| 桌面小组件 | 提供详细和紧凑两种组件布局，快速查看近期课程。 |

## 界面预览

截图按使用场景分组，浅色与深色界面并列展示，便于对比主题适配效果。

### 课表

<table>
  <tr>
    <td width="50%" align="center"><img src="docs/images/screenshot-schedule-light.webp" alt="浅色模式课程表" width="82%" /></td>
    <td width="50%" align="center"><img src="docs/images/screenshot-schedule-dark.webp" alt="深色模式课程表" width="82%" /></td>
  </tr>
  <tr>
    <td align="center"><sub>浅色模式</sub></td>
    <td align="center"><sub>深色模式</sub></td>
  </tr>
</table>

### 查询与成绩

<table>
  <tr>
    <td width="50%" align="center"><img src="docs/images/screenshot-rooms-liquid.webp" alt="空教室查询" width="82%" /></td>
    <td width="50%" align="center"><img src="docs/images/screenshot-grades-liquid.webp" alt="成绩查询" width="82%" /></td>
  </tr>
  <tr>
    <td align="center"><sub>空教室查询</sub></td>
    <td align="center"><sub>成绩查询</sub></td>
  </tr>
</table>

### 其他场景

<table>
  <tr>
    <td width="33%" align="center"><img src="docs/images/screenshot-exams-framed.webp" alt="考试安排" width="90%" /></td>
    <td width="33%" align="center"><img src="docs/images/screen-public-login.jpg" alt="全校课表登录" width="90%" /></td>
    <td width="33%" align="center"><img src="docs/images/screenshot-grade-detail-dark.webp" alt="成绩详情" width="90%" /></td>
  </tr>
  <tr>
    <td align="center"><sub>考试安排</sub></td>
    <td align="center"><sub>全校课表</sub></td>
    <td align="center"><sub>成绩详情</sub></td>
  </tr>
</table>

课表和成绩还支持导出为图片，桌面小组件提供详细与紧凑两种布局。

<p align="center">
  <img src="docs/images/export-class-schedule.webp" alt="课表导出" width="40%" />
  &nbsp;&nbsp;
  <img src="docs/images/export-transcript.webp" alt="成绩单导出" width="40%" />
</p>

## LiquidGlass 与个性化

- 底部导航栏、弹窗、按钮、成绩卡片、考试卡片和空教室结果统一采用液态玻璃视觉。
- 支持浅色模式、深色模式，以及跟随系统自动切换。
- 支持选择自定义背景图片，并调整缩放、位置和背景清晰度。
- 液态玻璃组件会根据当前主题和背景实时调整模糊、透明度、边框及文字颜色。
- 课程卡片在不同主题下使用独立的色彩方案，保持相邻课程和同一页面中的辨识度。

## 数据与提醒

- 课程、考试、成绩和空教室数据来自学校教务系统。
- 课程数据会缓存到本地，短暂断网时仍可查看已同步内容。
- 课程提醒仅安排下一节课程，触发后自动安排后续提醒，减少后台占用。
- 全校课表会通过数据变化检测更新本地缓存。

## 开始使用

1. 安装应用并使用学校教务系统账号登录。
2. 等待首次课程数据同步完成。
3. 通过底部导航进入课程表、考试、成绩或空教室页面。
4. 如需查看其他班级，在登录页切换到“全校课表”并选择班级。
5. 在“更多”中设置主题、背景图片和课程提醒。

> 教务系统维护或网络异常时，在线查询可能暂时不可用；已经缓存的数据仍可离线查看。

## 版本信息

| 项目 | 当前值 |
| --- | --- |
| 当前版本 | `0.3.8 Beta` |
| Version Code | `11` |
| 最低系统 | Android 8.0（API 26） |
| 开发语言 | Kotlin |
| UI 技术 | Jetpack Compose / Canvas |

<details>
<summary><strong>本地构建</strong></summary>

项目使用 Android Gradle Plugin 构建，需要 JDK 17。

```bash
# Windows
./gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```

Release APK 输出到：

```text
app/build/outputs/apk/release/WeSDAU_V0.3.8 Beta.apk
```

</details>

<br />

<div align="center">

**让每天的安排更从容一点。**

</div>
