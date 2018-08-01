# Bilibili Live Notification Telegram Bot
Bilibili 直播开播提醒 Telegram 机器人, 当目标直播间(复数)开始直播时, 将自动推送开播消息到目标 Telegram 群组(复数).

# 使用
安装 jre

    apt install openjdk-8-jre

在本项目 [releases](../../releases) 页面下载 jar

运行

    java -jar bilibili-live-notification-telegram-bot-{version}-all.jar

(version 为版本号)

首次运行后, 将在工作目录生成一份 config.json, 修改该文件, 填入所需的配置项后重启程序.

正确的配置文件大致如下

    {
      "logLevel": "INFO",
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

telegramBotConfig.username 与 telegramBotConfig.token 是从 BotFather 获取的, 如果你还不知道如何创建 Telegram Bot, 请参阅 https://core.telegram.org/bots

telegramBotConfig.creatorId 用于指定谁拥有 Bot 的超级管理员权限, 该用户可以执行 Bot 的数据库导入导出操作, 如果不需要使用这些功能, 可以指定该配置项的值为 0.

telegramBotConfig.httpProxyConfig 用于指定 HttpProxy 的启用与设置, 当总开关 useHttpProxy 为 false 时即不使用 HttpProxy.

注意, 在使用 HttpProxy 的情况下, 如果 HttpProxy 本身不能连接到 Telegram 服务器, 由于对于程序而言是连上 "服务器" 了, 所以会在很长时间之后才超时(程序抛出异常). 因此请首先使用其他方式确认 HttpProxy 工作正常.

liveRoomIds 为程序需要监听的直播间号码, 可以有多个.

# 构建
安装 jdk

    apt install openjdk-8-jdk

执行命令行

    ./gradlew shadowJar

# 普通命令
注意, 以下所指的 "命令" 都是 telegram 中的机器人指令.

/enable

在一个群组启用开播提醒, 使用该命令的用户必须为群组管理员.

/disable

在一个群组关闭开播提醒, 使用该命令的用户必须为群组管理员.

/room {roomId}

对一个直播间的状态进行查询, 可以是私聊也可以是群聊, 不需要任何权限.

# 管理命令
bot 的管理命令在初始状态下仅配置文件中的 creatorId 指定的用户(创建者, 等同超级管理员)可用, 但是可以通过 /promote {userId} 来将其他用户设为 bot 管理员.

管理员可以通过 /backup 命令来备份 bot 的数据库文件, 从而实现快速 bot 数据迁移.

管理员还可以通过 /ban 命令来禁止某个用户使用机器人.

更多管理命令和用法详见 https://github.com/rubenlagus/TelegramBots/wiki/Simple-Example#testing-your-bot

# 聊天栏命令补全
如果你需要你的机器人有聊天栏命令补全, 那么你就需要向 BotFather 注册你的机器人指令.

首先, 开启与 BotFather 的聊天 https://telegram.me/botfather

再执行命令 /setcommands

按照提示选择一个你的机器人(只能选择由当前用户创建的机器人)

之后 BotFather 会给出一个输入要求提示, 按照要求输入你的命令描述, 例如

    enable - 开启直播提醒
    disable - 关闭直播提醒
    room - [room {roomId}] 查询房间状态

然后无论是私聊还是群聊, 都可以看到命令补全了.

对于有参数的命令, 请在补全菜单中长按这个命令, 就会将该命令输入到输入框而不是直接发送出去.

# License
GPL V3
