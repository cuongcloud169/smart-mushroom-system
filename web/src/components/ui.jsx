import React, { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';

// Hook tải dữ liệu từ 1 đường dẫn API, trả về [dữ liệu, hàm tải lại]
export function useData(path, initial = []) {
  const [data, setData] = useState(initial);
  const reload = useCallback(() => {
    if (!path) return Promise.resolve();
    return api(path).then((d) => setData(d ?? initial)).catch((e) => alert(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path]);
  useEffect(() => { reload(); }, [reload]);
  return [data, reload];
}

// Bảng dữ liệu. cols = [{ label, key } hoặc { label, render: (row) => ... }]
export function Table({ rows, cols, actions, empty = 'Chưa có dữ liệu' }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{cols.map((c) => <th key={c.label}>{c.label}</th>)}{actions && <th />}</tr>
        </thead>
        <tbody>
          {rows.length === 0 && <tr><td colSpan={cols.length + 1} className="muted">{empty}</td></tr>}
          {rows.map((r, i) => (
            <tr key={r.id ?? r.event_type ?? i}>
              {cols.map((c) => <td key={c.label}>{c.render ? c.render(r) : String(r[c.key] ?? '')}</td>)}
              {actions && <td className="actions">{actions(r)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// Hộp thoại form dùng chung cho thêm/sửa.
// fields = [{ name, label, type: text|number|date|email|password|select|checkbox|textarea, options, required, step }]
export function FormModal({ title, fields, initial = {}, onSubmit, onClose, submitText = 'Lưu' }) {
  const [v, setV] = useState(() => {
    const init = { ...initial };
    fields.forEach((f) => { if (f.type === 'checkbox' && init[f.name] === undefined) init[f.name] = false; });
    return init;
  });
  const [busy, setBusy] = useState(false);
  const set = (name, value) => setV((old) => ({ ...old, [name]: value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const out = {};
      fields.forEach((f) => {
        let val = v[f.name];
        if (f.type === 'number') val = val === '' || val == null ? null : Number(val);
        if (f.type !== 'checkbox' && val === '') val = null;
        out[f.name] = val;
      });
      await onSubmit(out);
      onClose();
    } catch (err) {
      alert(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-bg" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h3>{title}</h3>
        {fields.map((f) => (
          <label key={f.name} className={f.type === 'checkbox' ? 'check' : ''}>
            {f.type !== 'checkbox' && <span>{f.label}{f.required && ' *'}</span>}
            {f.type === 'select' ? (
              <select value={v[f.name] ?? ''} required={f.required} onChange={(e) => set(f.name, e.target.value)}>
                <option value="">-- chọn --</option>
                {f.options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            ) : f.type === 'textarea' ? (
              <textarea value={v[f.name] ?? ''} onChange={(e) => set(f.name, e.target.value)} />
            ) : f.type === 'checkbox' ? (
              <><input type="checkbox" checked={!!v[f.name]} onChange={(e) => set(f.name, e.target.checked)} /> {f.label}</>
            ) : (
              <input type={f.type || 'text'} step={f.step} value={v[f.name] ?? ''} required={f.required}
                     onChange={(e) => set(f.name, e.target.value)} />
            )}
          </label>
        ))}
        <div className="row end">
          <button type="button" className="ghost" onClick={onClose}>Hủy</button>
          <button disabled={busy}>{busy ? 'Đang lưu...' : submitText}</button>
        </div>
      </form>
    </div>
  );
}

// Nhãn trạng thái có màu
export function Badge({ value, labels, tone }) {
  return <span className={'badge ' + (tone || value)}>{labels?.[value] ?? value}</span>;
}

// Thẻ số liệu
export function Card({ label, value, wide }) {
  return <div className={'card' + (wide ? ' wide' : '')}>{label}<b>{value}</b></div>;
}

// Gọi API rồi tải lại, hiện lỗi nếu có
export const act = (fn, reload) => async (...args) => {
  try { await fn(...args); await reload?.(); } catch (e) { alert(e.message); }
};
