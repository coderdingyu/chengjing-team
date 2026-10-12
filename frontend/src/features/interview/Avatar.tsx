import { useEffect, useRef } from "react";
import type { CSSProperties } from "react";
import { createAvatarScene, type AvatarLook, type AvatarSceneHandle } from "./avatarScene";
import "./interview.css";

export type { AvatarLook };

/**
 * Owner: member C. C06 — hosts the three-dimensional interviewer canvas.
 *
 * The scene is created once and the look is pushed into it afterwards, so switching avatars does not
 * build a new WebGL context on every click; the context is released when the component unmounts.
 */
export function InterviewerAvatar({ look }: { look: AvatarLook }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const handleRef = useRef<AvatarSceneHandle | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const handle = createAvatarScene(canvas, look);
    handleRef.current = handle;
    return () => {
      handle.dispose();
      handleRef.current = null;
    };
    // Created once on purpose: the look is applied by the effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    handleRef.current?.setLook(look);
  }, [look]);

  return (
    <div className="iv-avatar-stage" style={{ "--iv-avatar-accent": look.accent } as CSSProperties}>
      <canvas ref={canvasRef} className="iv-avatar-canvas" aria-hidden="true" />
      <span className="sr-only">三维面试官：{look.name}</span>
    </div>
  );
}
