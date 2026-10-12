/**
 * Owner: member C. C06 — builds the three-dimensional interviewer that sits in the interview room.
 *
 * The scene is deliberately small: one stylised figure assembled from primitives, lit by two
 * directional lights, with a slow idle motion. Nothing here is a photoreal avatar and nothing here
 * renders the candidate — the room only needs a present, switchable interviewer.
 *
 * Everything the caller gets back has to be disposed: `dispose()` releases the WebGL context, so
 * switching avatars or leaving the page does not leak a context per switch.
 */

import {
  AmbientLight,
  CapsuleGeometry,
  Clock,
  Color,
  DirectionalLight,
  Group,
  Mesh,
  MeshStandardMaterial,
  PerspectiveCamera,
  Scene,
  SphereGeometry,
  WebGLRenderer,
} from "three";

export type AvatarLook = {
  id: string;
  name: string;
  trait: string;
  accent: string;
  build: string;
};

export type AvatarSceneHandle = {
  setLook: (look: AvatarLook) => void;
  dispose: () => void;
};

/** Coarse body shapes the server catalog can ask for. Unknown values fall back to `slim`. */
const BUILD_SHAPE: Record<string, { width: number; height: number }> = {
  slim: { width: 1, height: 1 },
  broad: { width: 1.16, height: 0.96 },
  tall: { width: 0.94, height: 1.12 },
};

const SKIN = "#f0dccb";

export function createAvatarScene(canvas: HTMLCanvasElement, initialLook: AvatarLook): AvatarSceneHandle {
  const renderer = new WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new Scene();
  const camera = new PerspectiveCamera(34, 1, 0.1, 60);
  camera.position.set(0, 1.35, 4.4);
  camera.lookAt(0, 1.0, 0);

  scene.add(new AmbientLight(0xffffff, 1.25));
  const keyLight = new DirectionalLight(0xffffff, 1.7);
  keyLight.position.set(2.4, 3.6, 2.4);
  scene.add(keyLight);
  const rimLight = new DirectionalLight(0xffffff, 0.75);
  rimLight.position.set(-2.6, 1.8, -2.2);
  scene.add(rimLight);

  const figure = new Group();
  scene.add(figure);

  const materials: MeshStandardMaterial[] = [];
  const geometries: Array<SphereGeometry | CapsuleGeometry> = [];

  const makeMaterial = (color: string, roughness: number, metalness: number) => {
    const material = new MeshStandardMaterial({ color: new Color(color), roughness, metalness });
    materials.push(material);
    return material;
  };

  const skin = makeMaterial(SKIN, 0.72, 0.02);
  const outfit = makeMaterial(initialLook.accent, 0.42, 0.1);

  const headGeometry = new SphereGeometry(0.33, 32, 24);
  const bodyGeometry = new CapsuleGeometry(0.4, 1.05, 10, 22);
  const collarGeometry = new SphereGeometry(0.3, 24, 16);
  const plinthGeometry = new CapsuleGeometry(0.16, 0.1, 6, 14);
  geometries.push(headGeometry, bodyGeometry, collarGeometry, plinthGeometry);

  const head = new Mesh(headGeometry, skin);
  head.position.y = 1.76;
  figure.add(head);

  const neck = new Mesh(plinthGeometry, skin);
  neck.position.y = 1.44;
  figure.add(neck);

  const body = new Mesh(bodyGeometry, outfit);
  body.position.y = 0.84;
  figure.add(body);

  const collar = new Mesh(collarGeometry, outfit);
  collar.position.y = 1.38;
  collar.scale.set(1, 0.58, 1);
  figure.add(collar);

  const applyBuild = (build: string) => {
    const shape = BUILD_SHAPE[build] ?? BUILD_SHAPE.slim;
    figure.scale.set(shape.width, shape.height, shape.width);
  };

  let frame = 0;
  const clock = new Clock();
  const animate = () => {
    frame = requestAnimationFrame(animate);
    const t = clock.getElapsedTime();
    // A slower, smaller motion reads as "breathing" rather than as a spinning model.
    figure.position.y = Math.sin(t * 1.05) * 0.018;
    figure.rotation.y = Math.sin(t * 0.42) * 0.17;
    renderer.render(scene, camera);
  };

  const container = canvas.parentElement;
  const resize = () => {
    const width = container?.clientWidth || canvas.clientWidth || 320;
    const height = container?.clientHeight || canvas.clientHeight || 360;
    renderer.setSize(width, height, false);
    camera.aspect = width / Math.max(1, height);
    camera.updateProjectionMatrix();
  };
  const observer = new ResizeObserver(resize);
  if (container) observer.observe(container);
  resize();

  const setLook = (look: AvatarLook) => {
    outfit.color.set(look.accent);
    applyBuild(look.build);
  };
  setLook(initialLook);

  animate();

  return {
    setLook,
    dispose() {
      cancelAnimationFrame(frame);
      observer.disconnect();
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => material.dispose());
      renderer.dispose();
    },
  };
}
