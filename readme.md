# Casnic Surveillance Android Application

The simplest RTSP server that uses the phone's camera and microphone.

Uses code from [fyhertz/libstreaming](https://github.com/fyhertz/libstreaming/tree/2.2). The license is maintained at the top of those files, but the package definition was changed from `net.majorkernelpanic` to `com.assortedsolutions`. The initial commit of the files contain only the package definition change. All further changes are my own and retain the original license.

# How To Start

1. Connect physical phone to same network as the host machine that will run VLC
1. Start app from Android Studio
  - no need to press `START` in app because the RtspServer is started on load
1. Start stream in VLC
  - Open VLC
  - Media => Open Network Stream... => `rtsp://xxx.xxx.xxx.xxx:8086` (using IP of phone)

# Settings

Settings are controlled using `PreferenceManager`. In order to update settings, the following code can be used:

```java
Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
editor.putString(RtspServer.KEY_PORT), String.valueOf(8086));
editor.commit();
```

## RTSP Server

| Key         | Default Value |
| ----------- | ------------- |
| KEY_ENABLED | true          |
| KEY_PORT    | 8086          |
