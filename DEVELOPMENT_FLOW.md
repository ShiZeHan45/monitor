# 研发过程约定

## 1. Git 配置

### 仓库信息
- **仓库地址**: `https://github.com/ShiZeHan45/monitor.git`
- **开发分支**: `f_claude`

### SSH 服务器
- **地址**: `192.168.199.85`
- **SSH Key**: 已配置完成
- **jar 包存放路径**: `/soft/actuator`

### 开发工作目录
- **开发工作项目位置**: `Z:\monitor`
- **部署构建项目位置**: `D:\SZH\projects\AI`

---

## 2. 开发完成后流程

### 2.1 纯前端代码改动
1. 编写提交描述
2. 推送代码到远程仓库
3. 结束工作

### 2.2 含后端代码改动

**详细步骤**:

1. **提交代码**
   ```bash
   cd Z:\monitor
   git add .
   git commit -m "提交描述"
   git push origin f_claude
   ```

2. **拉取打包（编译检查 + 构建）**
   ```bash
   cd D:\SZH\projects\AI
   git pull origin f_claude
   mvn clean install -D"maven.test.skip"=true
   ```
   - **编译必须通过**，不通过则：
     - 回到 `Z:\monitor` 修改代码 → `git add . && git commit && git push`
     - 回到本步骤（`git pull + mvn install`）重新尝试
     - **循环直至编译通过**
   - 如有新页面产生，注意老页面访问时新页面入口要能正常显示，路由问题要处理好
   - 产物: `target/actuator.jar`

3. **上传到服务器**
   ```bash
   scp target/actuator.jar root@192.168.199.85:/soft/actuator/
   ```

4. **重启服务**
   ```bash
   ssh root@192.168.199.85 "systemctl stop actuator"
   ssh root@192.168.199.85 "systemctl start actuator"
   ```

5. **检查状态**
   ```bash
   ssh root@192.168.199.85 "systemctl status actuator"
   ```
   - 日志路径 /soft/actuator/app.log

---

## 3. 提交描述规范

### 格式要求
- 简洁明了，描述本次改动的核心内容
- 使用英文或中文均可，保持一致性

### 示例
```
feat: 新增SQL执行规则管理接口
fix: 修复Grafana日志查询超时问题
docs: 更新API文档
refactor: 优化SQL执行流程
```

---

## 4. 注意事项

1. **代码质量**: 确保代码符合项目编码规范，无语法错误
2. **依赖检查**: 确保新增依赖已添加到 `pom.xml`
3. **配置文件**: 敏感配置（如密码、密钥）不应提交到仓库
4. **错误处理**: 失败时需仔细排查日志，尝试解决问题
