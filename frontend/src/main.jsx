import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './style.css';

const maxFileSize = 20 * 1024 * 1024;

const markdownComponents = {
  table: ({ children }) => (
    <div className="answer-table" role="region" aria-label="Tabel i vurderingen" tabIndex={0}>
      <table>{children}</table>
    </div>
  ),
  pre: ({ children }) => <pre tabIndex={0}>{children}</pre>,
};

function App() {
  const [prompt, setPrompt] = useState('Vurder denne elevopgave og giv mig en karakter på 7-trinsskalaen (-3, 00, 02, 4, 7, 10, 12) ud fra vurderingsmaterialet. Begrund karakteren.');
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [answer, setAnswer] = useState(null);

  async function evaluate(event) {
    event.preventDefault();
    setError('');
    setAnswer(null);
    if (!prompt.trim()) return setError('Skriv en besked til vurderingen.');
    if (!file || !file.name.toLowerCase().endsWith('.pdf')) return setError('Vælg en PDF med elevens opgave.');
    if (file.size === 0 || file.size > maxFileSize) return setError('PDF-filen skal være mellem 1 byte og 20 MB.');

    const body = new FormData();
    body.append('prompt', prompt.trim());
    body.append('file', file);
    setLoading(true);
    try {
      const response = await fetch('/api/evaluate', { method: 'POST', body });
      const text = await response.text();
      let data;
      try { data = JSON.parse(text); } catch { /* Handle plain-text server errors below. */ }
      if (!response.ok) throw new Error(data?.message || text || `Backend svarede med fejl ${response.status}. Kontrollér, at den kører.`);
      if (!Array.isArray(data) || !data.every(item => typeof item === 'string') || !data.join('').trim()) {
        throw new Error('Backend returnerede ikke en vurdering. Prøv igen.');
      }
      setAnswer(data.join('\n\n'));
    } catch (err) {
      setError(err instanceof TypeError ? 'Kunne ikke kontakte backend. Kontrollér, at den kører på port 9393.' : err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main>
      <h1>Vurder elevopgave</h1>
      <p>Skriv din besked, og upload elevens opgave som PDF.</p>
      <form onSubmit={evaluate} aria-busy={loading}>
        <fieldset disabled={loading}>
          <label htmlFor="prompt">Din besked</label>
          <textarea id="prompt" rows="5" required maxLength={10000} value={prompt} onChange={event => setPrompt(event.target.value)} />
          <label htmlFor="file">Elevens opgave (PDF, maks. 20 MB)</label>
          <input id="file" type="file" accept=".pdf,application/pdf" required onChange={event => { setFile(event.target.files[0] || null); setError(''); setAnswer(null); }} />
          <button type="submit">{loading ? 'Vurderer…' : 'Vurder opgave'}</button>
        </fieldset>
      </form>
      {loading && <p role="status">Opgaven vurderes. Det kan tage et par minutter.</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {answer && (
        <section aria-live="polite">
          <h2>Vurdering</h2>
          <div className="answer">
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents} skipHtml>
              {answer}
            </ReactMarkdown>
          </div>
        </section>
      )}
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
