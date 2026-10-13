export default function ErrorBanner({ error }) {
  if (!error) return null;
  return <div className="banner">Could not reach the API ({error}). Is the backend running on port 8080?</div>;
}