# DutyPlugin - Minecraft Paper Plugin

A comprehensive duty tracking plugin for Minecraft Paper 1.21 with Discord integration.

## Features
- Multiple duty types with individual permissions (duty.NAME)
- Discord webhook logging for duty changes
- Time tracking for each duty type
- Commands: /duty, /checktime, /resettime
- Persistent data storage

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
   target\DutyPlugin-1.0.0.jar
   ```

### If you don't have Maven installed:

**Download Maven:**
1. Go to https://maven.apache.org/download.cgi
2. Download the Binary zip archive
3. Extract it to C:\Program Files\Maven
4. Add to PATH:
   - Search "Environment Variables" in Windows
   - Edit "Path" in System Variables
   - Add: C:\Program Files\Maven\bin
5. Restart Command Prompt and try: `mvn --version`

**Or use your IDE:**
- IntelliJ IDEA: Open the folder, right-click pom.xml → Maven → Reload Project → Maven → Package
- Eclipse: Import as Maven Project → Run As → Maven Build → Goals: clean package

## Installation

1. Place DutyPlugin-1.0.0.jar in your server's plugins/ folder
2. Start the server (creates config files)
3. Stop the server
4. Edit plugins/DutyPlugin/config.yml
5. Add your Discord webhook URL
6. Configure permissions in your permissions plugin
7. Start the server

## Permissions

- `duty.NAME` - Permission to go on duty for a specific type (e.g., duty.admin, duty.moderator)
- `duty.reset` - Permission to reset duty times
- `duty.*` - Access to all duty types

## Commands

- `/duty <NAME>` - Go on duty for a specific role
- `/duty` - Go off duty
- `/checktime <NAME>` - Check total time for a duty type
- `/resettime <NAME|ALL>` - Reset duty time (requires duty.reset permission)

## Discord Setup

1. Go to your Discord server settings
2. Navigate to Integrations → Webhooks
3. Create a new webhook for your desired channel
4. Copy the webhook URL
5. Paste it in config.yml under discord-webhook-url

## Support

For issues or questions, check the plugin files or modify the code as needed.
