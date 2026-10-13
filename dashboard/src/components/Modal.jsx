export default function Modal({ title, onClose, children }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(event) => event.stopPropagation()}>
        <div className="card-header">
          <h3>{title}</h3>
          <button className="ghost" onClick={onClose}>Close</button>
        </div>
        {children}
      </div>
    </div>
  );
}