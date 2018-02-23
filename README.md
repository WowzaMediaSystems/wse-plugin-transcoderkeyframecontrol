# TranscoderKeyFrameControl
The **TranscoderKeyFrameControl** module for [Wowza Streaming Engine™ media server software](https://www.wowza.com/products/streaming-engine) can be used to insert key frames into the transcoded streams at regular intervals. It overrides the original key frame settings and will ensure that the keyframes are aligned between each rendition.

## Prerequisites
Wowza Streaming Engine 4.6.0 or later is required.

Wowza Transcoder must be enabled in the Wowza Streaming Engine application.

## Usage
To enable the module, add the following module definition to your application configuration.

Name | Description | Fully Qualified Class Name
--- | --- | ---
ModuleTranscoderVideoKeyFrameControl | Insert aligned key frames into transcoded streams. | com.wowza.wms.plugin.ModuleTranscoderVideoKeyFrameControl

The following properties can be used to configure the module.

Path | Name | Value | Comment
--- | --- | --- | ---
/Root/Application/ | transcoderGopInterval | 2000 | default 2000ms
/Root/Application/ | transcoderGopIntervalDebugLog | true | default false

The key frames are inserted each time the stream timecode rolls over the `transcoderGopInterval` value so if the transcoding starts on a non-zero timecode then the first GOP may be smaller than the `transcoderGopInterval` value.

## More resources
[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.

To use the compiled version of this module, see [How to insert aligned key frames into Wowza Transcoder streams (TranscoderKeyFrameControl)](https://www.wowza.com/docs).

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](https://github.com/WowzaMediaSystems/wse-plugin-transcodertimedsnapshot/blob/master/LICENSE.txt).

![alt tag](http://wowzalogs.com/stats/githubimage.php?plugin=wse-plugin-transcoderkeyframecontrol)
