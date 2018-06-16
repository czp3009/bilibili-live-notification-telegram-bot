# Bilibili Live Notification Telegram Bot
Bilibili 直播开播提醒 Telegram 机器人, 当目标直播间(复数)开始直播时, 将自动推送开播消息到目标 Telegram 群组(复数).

# 使用
安装 jre

    apt install openjdk-8-jre

在本项目 [releases](../../releases) 页面下载 jar

运行

    java -jar bilibili-live-notification-telegram-bot-{version}.jar

(version 为版本号)

首次运行后, 将在工作目录生成一份 config.json, 修改该文件, 填入所需的配置项后重启程序.

正确的配置文件大致如下

    {
      "telegramBotConfig": {
        "username": "czp_bot",
        "token": "381487180:AHFRIcngHfYZ7JihahXVB3zqkqpIUjTQrdk",
        "creatorId": 196664407,
        "httpProxyConfig": {
          "useHttpProxy": false,
          "hostName": "localhost",
          "port": 1080,
          "authenticationEnabled": false,
          "proxyUser": "proxyUser",
          "proxyPassword": "proxyPassword"
        }
      },
      "liveRoomIds": [1110317]
    }

telegramBotConfig.username 与 telegramBotConfig.token 是从 Bot Father 获取的, 如果你还不知道如何创建 Telegram Bot, 请参阅 https://core.telegram.org/bots

telegramBotConfig.creatorId 用于指定谁拥有 Bot 的超级管理员权限, 该用户可以执行 Bot 的数据库导入导出操作, 如果不需要使用这些功能, 可以指定该配置项的值为 0.

telegramBotConfig.httpProxyConfig 用于指定 HttpProxy 的启用与设置, 当总开关 useHttpProxy 为 false 时即不使用 HttpProxy.

注意, 在使用 HttpProxy 的情况下, 如果 HttpProxy 本身不能连接到 Telegram 服务器, 由于对于程序而言是连上 "服务器" 了, 所以会在很长时间之后才超时(程序抛出异常). 因此请首先使用其他方式确认 HttpProxy 工作正常.

# 命令
注意, 这里说的命令都是在 Telegram 里用的

/enable


