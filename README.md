# DutyPlugin - Minecraft Paper Plugin

A comprehensive duty tracking plugin for Minecraft Paper 1.21 with Discord integration.

## Features
- Multiple duty types with individual permissions (duty.NAME)
- Discord webhook logging for duty changes
- Time tracking for each duty type
- Check your own time or other players' times
- Paginated leaderboard of all players' duty times
- Commands: /duty, /checktime, /dutytimes, /resettime
- Persistent data storage

## Commands

### Player Commands
- `/duty <NAME>` - Go on duty for a specific role
- `/duty` - Go off duty
- `/checktime <DUTY>` - Check your own time for a duty type
- `/checktime <PLAYER> <DUTY>` - Check another player's time (requires `duty.checkothers`)

### Admin Commands
- `/duty reload` - Reload config (requires `duty.reload`)
- `/dutytimes <DUTY> [page]` - View leaderboard for a duty type (requires `duty.viewall`)
- `/resettime <DUTY|ALL>` - Reset your duty times (requires `duty.reset`)

## Permissions

### Admin Permissions
- `duty.reload` - Reload the plugin config
- `duty.checkothers` - Check other players' duty times
- `duty.viewall` - View duty time leaderboards
- `duty.reset` - Reset duty times
- `duty.*` - All permissions

### Duty Permissions
- `duty.staff` - Go on Staff duty
- `duty.admin` - Go on Admin duty
- `duty.moderator` - Go on Moderator duty
- `duty.support` - Go on Support duty
- `duty.builder` - Go on Builder duty
- `duty.helper` - Go on Helper duty

## Configuration

Edit `plugins/DutyPlugin/config.yml`:

```yaml
# Discord Webhook URL
discord-webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_HERE"

# Define your duty types
duties:
  Staff:
    permission: "duty.staff"
  Admin:
    permission: "duty.admin"
  # Add more as needed!
```

## Examples

### Check Your Own Time
```
/checktime Staff
> Your total time for Staff: 5h 30m 15s
```

### Check Another Player's Time
```
/checktime notnico22 Staff
> notnico22's total time for Staff: 12h 45m 30s
```

### View Leaderboard
```
/dutytimes Staff
═══════════════════════════════════════
Duty Times for Staff (Page 1/2)
═══════════════════════════════════════
#1. notnico22 - 12h 45m 30s
#2. PlayerTwo - 8h 20m 15s
#3. PlayerThree - 5h 10m 0s
#4. PlayerFour - 3h 5m 45s
...
═══════════════════════════════════════
Use /dutytimes Staff 2 for next page
```

## Building the JAR

### Prerequisites
- Java Development Kit (JDK) 21 or higher
- Apache Maven

### Build Steps

1. Open Command Prompt or PowerShell
2. Navigate to the plugin directory:
   ```
   cd C:\Users\greys\Desktop\DutyPlugin
   ```
3. Run Maven to build:
   ```
   mvn clean package
   ```
4. The JAR file will be created at:
   ```
   target\DutyPlugin-1.2.0.jar
   ```

### Using GitHub Actions (Automated)

1. Push changes to GitHub:
   ```bash
   git add .
   git commit -m "Update to v1.2.0"
   git push
   ```
2. Go to your repository's Actions tab
3. Wait for the build to complete
4. Download the artifact

## Installation

1. Place DutyPlugin-1.2.0.jar in your server's plugins/ folder
2. Start the server (creates config files)
3. Stop the server
4. Edit plugins/DutyPlugin/config.yml
5. Add your Discord webhook URL
6. Configure permissions in your permissions plugin
7. Start the server

## Discord Setup

1. Go to your Discord server settings
2. Navigate to Integrations → Webhooks
3. Create a new webhook for your desired channel
4. Copy the webhook URL
5. Paste it in config.yml under discord-webhook-url

## Changelog

### v1.2.0
- Added ability to check other players' duty times
- Added `/dutytimes` command for paginated leaderboards
- Added colored rankings (#1 gold, #2 silver, #3 yellow)
- Added `duty.checkothers` permission
- Added `duty.viewall` permission

### v1.1.0
- Added config-based duty types
- Added `/duty reload` command
- Better error messages

### v1.0.0
- Initial release

## Support

For issues or questions, check the plugin files or modify the code as needed.
