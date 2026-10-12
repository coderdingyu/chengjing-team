import { useCallback, useEffect, useRef, useState } from "react";
import { registerStream, releaseStream } from "./localCapture";
import "./interview.css";

/**
 * Owner: member C. C06 — the candidate's own camera preview, kept entirely on this machine.
 *
 * Privacy rules this component exists to enforce:
 * <ul>
 *   <li>the camera is a separate consent — nothing is requested until the candidate agrees here;</li>
 *   <li>closing the preview stops the tracks, so the camera light does not stay on;</li>
 *   <li>nothing is uploaded: the stream goes into a local {@code <video>} and nowhere else.</li>
 * </ul>
 *
 * The stream is also registered with {@link registerStream}, which is what lets C04's pause / end
 * stop the microphone even when the user never touched this panel.
 */

type Props = {
  /** Server-side record of the camera decision, so a refresh shows the same state. */
  consented: boolean;
  onConsentChange: (granted: boolean) => void;
};

export function LocalVideoPreview({ consented, onConsentChange }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [wantCamera, setWantCamera] = useState(true);
  const [wantMic, setWantMic] = useState(false);
  const [active, setActive] = useState(false);
  const [error, setError] = useState("");

  const stop = useCallback(() => {
    const stream = streamRef.current;
    if (stream) {
      stream.getTracks().forEach((track) => track.stop());
      releaseStream(stream);
      streamRef.current = null;
    }
    if (videoRef.current) videoRef.current.srcObject = null;
    setActive(false);
  }, []);

  // Unmounting (leaving the room, ending the interview) must not leave the camera on.
  useEffect(() => stop, [stop]);

  const start = useCallback(async () => {
    setError("");
    if (!wantCamera && !wantMic) {
      setError("请至少选择摄像头或麦克风中的一项。");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("当前浏览器不支持本机预览。");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: wantCamera,
        audio: wantMic,
      });
      streamRef.current = stream;
      /** Registered so pausing or ending the interview can stop these tracks too. */
      registerStream(stream);
      if (videoRef.current && wantCamera) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play().catch(() => undefined);
      }
      setActive(true);
    } catch {
      setError("无法获取本机设备，请检查浏览器的摄像头/麦克风权限。");
      stop();
    }
  }, [stop, wantCamera, wantMic]);

  const agreeAndStart = () => {
    onConsentChange(true);
    void start();
  };

  const closeAndWithdraw = () => {
    stop();
    onConsentChange(false);
  };

  return (
    <div className="iv-preview">
      <div className="iv-preview-head">
        <strong>本机视频预览</strong>
        <span className="iv-muted">仅供你自己查看，不会上传</span>
      </div>

      {active ? (
        <div className="iv-preview-frame">
          {wantCamera ? (
            <video ref={videoRef} className="iv-preview-video" muted playsInline />
          ) : (
            <p className="iv-muted iv-preview-audio-only">
              仅开启麦克风（本机试听）。暂停或结束后轨道会停止。
            </p>
          )}
        </div>
      ) : (
        <p className="iv-muted iv-preview-idle">
          {consented ? "已同意开启本机预览，点击下方按钮开始。" : "尚未开启：需要你单独同意。"}
        </p>
      )}

      {!active && (
        <fieldset className="iv-preview-options">
          <legend>本次要使用</legend>
          <label>
            <input
              type="checkbox"
              checked={wantCamera}
              onChange={(event) => setWantCamera(event.target.checked)}
            />
            摄像头
          </label>
          <label>
            <input
              type="checkbox"
              checked={wantMic}
              onChange={(event) => setWantMic(event.target.checked)}
            />
            麦克风（本机试听）
          </label>
        </fieldset>
      )}

      <div className="iv-preview-actions">
        {!consented && (
          <button type="button" className="iv-primary" onClick={agreeAndStart}>
            同意并开启预览
          </button>
        )}
        {consented && !active && (
          <>
            <button type="button" className="iv-control-btn iv-control-strong" onClick={() => void start()}>
              开启预览
            </button>
            <button type="button" className="iv-control-btn" onClick={() => onConsentChange(false)}>
              撤回同意
            </button>
          </>
        )}
        {active && (
          <button type="button" className="iv-control-btn iv-control-danger" onClick={closeAndWithdraw}>
            关闭预览并停止摄像头
          </button>
        )}
      </div>

      {error && (
        <p role="alert" className="iv-error">
          {error}
        </p>
      )}
    </div>
  );
}
