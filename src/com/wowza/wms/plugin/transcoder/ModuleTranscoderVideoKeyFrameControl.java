/**
 * This code and all components (c) Copyright 2006 - 2018, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.transcoder;

import java.util.List;

import com.wowza.util.FLVUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.livetranscoder.ILiveStreamTranscoder;
import com.wowza.wms.stream.livetranscoder.LiveStreamTranscoderNotifyBase;
import com.wowza.wms.transcoder.model.LiveStreamTranscoder;
import com.wowza.wms.transcoder.model.LiveStreamTranscoderActionNotifyBase;
import com.wowza.wms.transcoder.model.TranscoderDecodedFrame;
import com.wowza.wms.transcoder.model.TranscoderSession;
import com.wowza.wms.transcoder.model.TranscoderSessionVideo;
import com.wowza.wms.transcoder.model.TranscoderSessionVideoEncode;
import com.wowza.wms.transcoder.model.TranscoderStream;
import com.wowza.wms.transcoder.model.TranscoderStreamDestinationVideo;
import com.wowza.wms.transcoder.model.TranscoderVideoEncodeFrameContext;
import com.wowza.wms.transcoder.model.TranscoderVideoEncoderNotifyBase2;

public class ModuleTranscoderVideoKeyFrameControl extends ModuleBase
{
	private static final String CLASSNAME = "ModuleTranscoderVideoKeyFrameControl";

	private WMSLogger logger = null;
	private IApplicationInstance appInstance = null;

	class TranscoderCreateNotifier extends LiveStreamTranscoderNotifyBase
	{
		TranscoderActionNotifier transcoderActionNotifier = new TranscoderActionNotifier();

		@Override
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
			logger.info(CLASSNAME + "#TranscoderCreateNotifier.onLiveStreamTranscoderCreate[" + appInstance.getContextStr() + "]: " + stream.getName(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

			// Add a live stream transcoder action listener so we can plug into the transcoding pipeline
			((LiveStreamTranscoder)liveStreamTranscoder).addActionListener(transcoderActionNotifier);
		}
	}

	class TranscoderActionNotifier extends LiveStreamTranscoderActionNotifyBase
	{

		// Called when the transcoding session is setup and ready to start
		@Override
		public void onInitStop(LiveStreamTranscoder liveStreamTranscoder)
		{
			logger.info(CLASSNAME + "#TranscoderActionNotifier.onInitStop[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

			try
			{
				while (true)
				{
					TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
					if (transcoderStream == null)
						break;

					TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
					TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();

					List<TranscoderSessionVideoEncode> videoEncodes = transcoderVideoSession.getEncodes();
					for (TranscoderSessionVideoEncode videoEncode : videoEncodes)
					{
						logger.info(CLASSNAME + "#TranscoderActionNotifier.onInitStop[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "] Add frame listener: rendition:" + videoEncode.getName(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

						// add a transcoder video frame listener
						videoEncode.addFrameListener(new TranscoderVideoEncoderNotifier(liveStreamTranscoder));
					}
					break;
				}
			}
			catch (Exception e)
			{
				logger.error(CLASSNAME + "#TranscoderActionNotifier.onInitStop[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "]  ", e);
			}
		}
	}

	class TranscoderVideoEncoderNotifier extends TranscoderVideoEncoderNotifyBase2
	{
		private LiveStreamTranscoder liveStreamTranscoder = null;
		private boolean debugLog = false;
		private long gopStartTimecode = -1;
		private long gopInterval = 2000;
		private long previousFrameTimecode = -1;
		private long previousFrameCount = -1;
		private long gopSize = 0;

		public TranscoderVideoEncoderNotifier(LiveStreamTranscoder liveStreamTranscoder)
		{
			this.liveStreamTranscoder = liveStreamTranscoder;
			debugLog = appInstance.getProperties().getPropertyBoolean("transcoderGopIntervalDebugLog", debugLog);
			gopInterval = appInstance.getProperties().getPropertyLong("transcoderGopInterval", gopInterval);
		}

		@Override
		public void onBeforeEncodeFrame(TranscoderSessionVideoEncode sessionVideoEncode, TranscoderStreamDestinationVideo destinationVideo, TranscoderVideoEncodeFrameContext frameContext)
		{
			TranscoderDecodedFrame decodedFrame = frameContext.getFrameHolder().getEncoderNextFrame();
			if (decodedFrame != null)
			{
				int frameType = FLVUtils.FLV_PFRAME;
				long frameTimecode = decodedFrame.getTimecode();
				long frameCount = frameContext.getFrameCount();
				if(previousFrameCount != -1 && previousFrameCount + 1 != frameCount)
				{
					if (debugLog)
						logger.warn(CLASSNAME + "#TranscoderVideoEncoderNotifier.onBeforeEncodeFrame[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "(" + destinationVideo.getDestination().getName() + ")" + "]  Bad frame count: " + previousFrameCount + ":" + frameCount + ", decodedFrame [dnf:enf:ft:tc]: " + decodedFrame.decoderNextFrame + ":" + decodedFrame.encoderNextFrame + ":" + decodedFrame.frameType + ":" + decodedFrame.timecode + ", packet: " + frameContext.getFrameHolder().getPacket(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
				}
				previousFrameCount = frameCount;

				// every gopInterval or timecode jump-back we are going to force a key frame
				if (frameTimecode >= gopStartTimecode || frameTimecode <= previousFrameTimecode)
				{
					if (frameTimecode <= previousFrameTimecode)
					{
						if (debugLog)
							logger.info(CLASSNAME + "#TranscoderVideoEncoderNotifier.onBeforeEncodeFrame[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "(" + destinationVideo.getDestination().getName() + ")" + "]  Timecodes jumped backwards: " + previousFrameTimecode
									+ " -> " + frameTimecode + ", forcing key frame", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

					}
					frameType = FLVUtils.FLV_KFRAME;
					if (debugLog)
						logger.info(CLASSNAME + "#TranscoderVideoEncoderNotifier.onBeforeEncodeFrame[" + appInstance.getContextStr() + "/" + liveStreamTranscoder.getStreamName() + "(" + destinationVideo.getDestination().getName() + ")" + "]  Insert key frame: gopStartTimecode:" + gopStartTimecode
								+ ", timecode:" + frameTimecode + ", frameCount:" + frameCount + ", lastGopSize:" + gopSize, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

					gopStartTimecode = ((frameTimecode / gopInterval) + 1) * gopInterval;
					gopSize = 1;
				}
				else
				{
					gopSize++;
				}
				
				previousFrameTimecode = frameTimecode;

				frameContext.setFrameType(frameType);
			}
		}
	}

	public void onAppStart(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		logger.info(CLASSNAME + ".onAppStart[" + appInstance.getContextStr() + "]: Build #4.", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		this.appInstance = appInstance;

		appInstance.addLiveStreamTranscoderListener(new TranscoderCreateNotifier());
	}
}
