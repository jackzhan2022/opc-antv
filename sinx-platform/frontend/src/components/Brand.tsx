export function Brand({ inverse = false }: { inverse?: boolean }) {
 return <div className={'brand' + (inverse ? ' brand-inverse' : '')}>
  <span className="brand-symbol" aria-hidden="true"><svg viewBox="0 0 32 32"><path d="M8 7h6l10 18h-6L8 7Zm10 0h6l-7 12-3-5 4-7ZM8 25l5-9 3 5-2 4H8Z" fill="currentColor" /></svg></span>
  <span><strong>信计 X 平台</strong><small>SinX Platform</small></span>
 </div>;
}
