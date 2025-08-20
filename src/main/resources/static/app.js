document.getElementById('ask').onclick = async () => {
  const question = document.getElementById('question').value;
  const resp = await fetch('/api/query', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question })
  });
  const data = await resp.json();
  document.getElementById('answer').textContent = data.answer;
};

// existing ask behavior preserved; workflow buttons are handled inline in index.html