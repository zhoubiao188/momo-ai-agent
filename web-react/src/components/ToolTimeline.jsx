import { Tooltip } from 'antd'
import { prettyJson } from '../markdown'

const STATUS_TEXT = { running: '执行中', ok: '成功', fail: '失败' }

export default function ToolTimeline({ items }) {
  if (!items.length) {
    return (
      <div className="timeline empty">
        工具调用会实时出现在这里：查订单 → 查日志 → 查手册 →（可选）退款。
      </div>
    )
  }

  return (
    <div className="timeline">
      {items.map((item) => (
        <div className="tool" key={item.id}>
          <div className="tool-top">
            <span className={`dot ${item.status}`} />
            <span className="tool-name">{item.name}</span>
            <span className="tool-meta">
              {item.step ? `step ${item.step}` : ''}
              {item.elapsedMs != null ? ` · ${item.elapsedMs}ms` : ''}
            </span>
            <Tooltip title={STATUS_TEXT[item.status] || ''}>
              <span className={`badge ${item.status}`}>{STATUS_TEXT[item.status]}</span>
            </Tooltip>
          </div>
          {item.args && <pre>{prettyJson(item.args)}</pre>}
          {item.result && <pre>{item.result}</pre>}
        </div>
      ))}
    </div>
  )
}
