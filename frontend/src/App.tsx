import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useParams } from "react-router-dom";
import { api } from "./api";
import { modules } from "./modules";
import PreparationPlans from "./features/preparation/PreparationPlans";
import PreparationNodes from "./features/preparation/PreparationNodes";
import PreparationStories from "./features/preparation/PreparationStories";
import PreparationPractices from "./features/preparation/PreparationPractices";

function Home() {
  return (
    <>
      <section className="hero">
        <span className="eyebrow">CHENGJING · TEAM EDITION</span>
        <h1>
          让真实经历，
          <br />
          <em>成为面试中的底气。</em>
        </h1>
        <p>
          这是五人协作的可运行骨架。下面的功能仍待实现，每完成一项就提交一个有测试的改动。
        </p>
        <a
          className="hero-link"
          href="https://github.com/coderdingyu/chengjing"
          target="_blank"
          rel="noreferrer"
        >
          查看现有系统参考 ↗
          <span className="sr-only">（在新窗口打开）</span>
        </a>
        <div className="orb" aria-hidden="true">
          澄
        </div>
      </section>
      <div className="section-heading">
        <span>五个模块 · 三十项功能</span>
        <span>30 次真实提交，由五位成员完成</span>
      </div>
      <div className="module-grid">
        {modules.map((item, index) => (
          <Link
            key={item.id}
            className="module-card"
            to={`/modules/${item.id}`}
          >
            <span className="module-index">
              0{index + 1} / {item.owner}
            </span>
            <h2>{item.title}</h2>
            <p>{item.summary}</p>
            <span className="card-foot">
              6 项待实现 <span>查看任务 ↗</span>
            </span>
          </Link>
        ))}
      </div>
    </>
  );
}

function ModulePage() {
  const { moduleId } = useParams();
  const item = modules.find((module) => module.id === moduleId);
  if (!item) return <Navigate to="/" replace />;
  return (
    <div className="module-page">
      <Link to="/" className="back">
        ← 返回工作台
      </Link>
      <span className="eyebrow">{item.owner} / 负责模块</span>
      <h1>{item.title}</h1>
      <p>{item.summary}</p>
      <div className="feature-list">
        {item.features.map((feature, index) => (
          <div key={feature} className="feature-row">
            <span>{String(index + 1).padStart(2, "0")}</span>
            <strong>{feature}</strong>
            <small>待实现</small>
          </div>
        ))}
      </div>
      <p className="footnote">
        每项的接口、旧系统代码位置和验收条件见仓库 docs/30项功能分工.md。
      </p>
    </div>
  );
}

export default function App() {
  const [health, setHealth] = useState("后端未连接");
  useEffect(() => {
    api<{ status: string }>("/system/health")
      .then((result) =>
        setHealth(result.status === "UP" ? "骨架服务已连接" : "后端未连接"),
      )
      .catch(() => setHealth("后端未连接"));
  }, []);
  return (
    <div className="shell">
      <header className="topbar">
        <Link className="brand" to="/">
          <span>澄</span>
          <strong>澄镜</strong>
          <small>团队协作版</small>
        </Link>
        <nav>
          <Link to="/">工作台</Link>
          <a
            href="https://github.com/coderdingyu/chengjing-team"
            target="_blank"
            rel="noreferrer"
          >
            团队仓库 ↗
          </a>
        </nav>
        <span className="health">
          <i />
          {health}
        </span>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/modules/preparation/plans" element={<PreparationPlans />} />
          <Route path="/modules/preparation/plans/:planId/nodes" element={<PreparationNodes />} />
          <Route path="/modules/preparation/stories" element={<PreparationStories />} />
          <Route
            path="/modules/preparation/plans/:planId/nodes/:nodeId/practice"
            element={<PreparationPractices />}
          />
          <Route path="/modules/:moduleId" element={<ModulePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <footer>
        澄镜 · 面向求职者的 AI 面试成长系统{" "}
        <span>先把基础做稳，再逐项接上能力。</span>
      </footer>
    </div>
  );
}
