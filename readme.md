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
