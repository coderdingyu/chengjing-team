import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api";
import "./stories.css";

type Story = {
  id: string;
  title: string;
  content: string;
  source: string;
  confirmedByOwner: boolean;
  sourceLabel: string;
  tags: string[];
  role: string | null;
  updatedAt: string;
};

const SOURCE_LABEL: Record<string, string> = {
  SELF: "本人经历",
  SCENARIO: "模拟情境",
};

export default function PreparationStories() {
  const [stories, setStories] = useState<Story[]>([]);
  const [keyword, setKeyword] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [source, setSource] = useState("SELF");
  const [role, setRole] = useState("");
  const [tags, setTags] = useState("");
  const [editingId, setEditingId] = useState("");
  const [draftTitle, setDraftTitle] = useState("");
  const [draftContent, setDraftContent] = useState("");
  const [draftTags, setDraftTags] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const query = new URLSearchParams();
    if (keyword.trim()) query.set("keyword", keyword.trim());
    if (sourceFilter) query.set("source", sourceFilter);
    const suffix = query.toString() ? "?" + query.toString() : "";
    api<Story[]>("/preparation/stories" + suffix, { signal: controller.signal })
      .then((next) => {
        if (!controller.signal.aborted) setStories(next);
      })
      .catch((e: Error) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [keyword, sourceFilter, reload]);

  function splitTags(value: string) {
    return value
      .split(/[,\uFF0C\s]+/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api<Story>("/preparation/stories", {
        method: "POST",
        body: JSON.stringify({
          title,
          content,
          source,
          role: role || null,
          tags: splitTags(tags),
        }),
      });
      setTitle("");
      setContent("");
      setTags("");
      setReload((n) => n + 1);
      setNotice(
        source === "SELF"
          ? "已保存。核对无误后请点「确认是本人事实」。"
          : "已保存为模拟情境，它不会被标记成本人事实。",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function startEdit(story: Story) {
    setEditingId(story.id);
    setDraftTitle(story.title);
    setDraftContent(story.content);
    setDraftTags(story.tags.join("、"));
    setError("");
    setNotice("");
  }

  async function saveEdit(story: Story) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<Story>(`/preparation/stories/${story.id}`, {
        method: "PATCH",
        body: JSON.stringify({ title: draftTitle, content: draftContent, tags: splitTags(draftTags) }),
      });
      setStories((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setEditingId("");
      setNotice("素材已更新。");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function confirmStory(story: Story, confirmed: boolean) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<Story>(`/preparation/stories/${story.id}/confirmation`, {
        method: "PATCH",
        body: JSON.stringify({ confirmed }),
      });
      setStories((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice(confirmed ? "已确认这是本人真实经历。" : "已撤销确认。");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(story: Story) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api<{ deletedId: string }>(`/preparation/stories/${story.id}`, { method: "DELETE" });
      setStories((current) => current.filter((item) => item.id !== story.id));
      setNotice(`已删除「${story.title}」。`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="preparation">
      <Link to="/" className="back">← 返回工作台</Link>
      <span className="eyebrow">面试准备 / 个人经历素材库</span>
      <h1>把讲过的经历收进自己的素材库</h1>
      <p>真实经历由你本人核对后确认；从公开岗位案例推演的内容会一直标成模拟情境。</p>

      <form className="plan-form" onSubmit={create}>
        <label htmlFor="story-title">素材标题</label>
        <input
          id="story-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="例如：对账链路重构"
          required
        />
        <label htmlFor="story-content">素材内容</label>
        <textarea
          id="story-content"
          rows={4}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="写清你做了什么、当时的约束和你自己的判断，不要写他人隐私信息"
          required
        />
        <label htmlFor="story-source">素材来源</label>
        <select id="story-source" value={source} onChange={(e) => setSource(e.target.value)}>
          <option value="SELF">本人经历（我真实做过的）</option>
          <option value="SCENARIO">模拟情境（公开案例推演）</option>
        </select>
        <label htmlFor="story-role">关联岗位（可选）</label>
        <input
          id="story-role"
          value={role}
          onChange={(e) => setRole(e.target.value)}
          placeholder="例如：后端工程师"
        />
        <label htmlFor="story-tags">标签（可选，用逗号分隔）</label>
        <input
          id="story-tags"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          placeholder="例如：结算、重构"
        />
        <button type="submit" disabled={busy}>保存素材</button>
      </form>

      <div className="story-search">
        <label htmlFor="story-keyword">搜索我的素材</label>
        <input
          id="story-keyword"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="搜标题、内容、岗位或标签"
        />
        <label htmlFor="story-filter">只看</label>
        <select id="story-filter" value={sourceFilter} onChange={(e) => setSourceFilter(e.target.value)}>
          <option value="">全部来源</option>
          <option value="SELF">本人经历</option>
          <option value="SCENARIO">模拟情境</option>
        </select>
      </div>

      {loading && <p role="status">正在加载素材…</p>}
      {error && (
        <div role="alert" className="plan-alert">
          <p>{error}</p>
          <button onClick={() => setReload((n) => n + 1)}>重新加载</button>
        </div>
      )}
      {notice && <p role="status">{notice}</p>}

      {!loading && stories.length === 0 && <p>还没有匹配的素材。先记下一条你真实做过的经历。</p>}
      <ul className="plan-list">
        {stories.map((story) => (
          <li key={story.id}>
            <div className="plan-head">
              <strong>{story.title}</strong>
              <span className={story.confirmedByOwner ? "story-tag ok" : "story-tag"}>
                {story.sourceLabel}
              </span>
            </div>
            {editingId === story.id ? (
              <>
                <label htmlFor={`edit-title-${story.id}`}>标题</label>
                <input
                  id={`edit-title-${story.id}`}
                  value={draftTitle}
                  onChange={(e) => setDraftTitle(e.target.value)}
                />
                <label htmlFor={`edit-content-${story.id}`}>内容</label>
                <textarea
                  id={`edit-content-${story.id}`}
                  rows={4}
                  value={draftContent}
                  onChange={(e) => setDraftContent(e.target.value)}
                />
                <label htmlFor={`edit-tags-${story.id}`}>标签</label>
                <input
                  id={`edit-tags-${story.id}`}
                  value={draftTags}
                  onChange={(e) => setDraftTags(e.target.value)}
                />
                <div className="plan-actions">
                  <button disabled={busy} onClick={() => saveEdit(story)}>保存修改</button>
                  <button disabled={busy} onClick={() => setEditingId("")}>取消</button>
                </div>
              </>
            ) : (
              <>
                <p>{story.content}</p>
                <p className="footnote">
                  {SOURCE_LABEL[story.source] ?? story.source}
                  {story.role ? ` · ${story.role}` : ""}
                  {story.tags.length > 0 ? ` · ${story.tags.join("、")}` : ""}
                </p>
                <div className="plan-actions">
                  <button disabled={busy} onClick={() => startEdit(story)}>编辑</button>
                  {story.source === "SELF" && !story.confirmedByOwner && (
                    <button disabled={busy} onClick={() => confirmStory(story, true)}>确认是本人事实</button>
                  )}
                  {story.source === "SELF" && story.confirmedByOwner && (
                    <button disabled={busy} onClick={() => confirmStory(story, false)}>撤销确认</button>
                  )}
                  <button disabled={busy} onClick={() => remove(story)}>删除</button>
                </div>
              </>
            )}
          </li>
        ))}
      </ul>
      <p className="footnote">
        素材只属于你自己的账号，别人打开会收到「属于其他账号，不能查看或修改」。
      </p>
    </section>
  );
}
