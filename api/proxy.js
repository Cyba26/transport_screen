export default async function handler(req, res) {
  const apiKey = process.env.PRIM_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ error: 'API key not configured' });
  }

  const { endpoint, ...params } = req.query;

  const allowed = ['stop-monitoring', 'general-message'];
  if (!endpoint || !allowed.includes(endpoint)) {
    return res.status(400).json({ error: 'Invalid endpoint' });
  }

  const query = new URLSearchParams(params).toString();
  const url = `https://prim.iledefrance-mobilites.fr/marketplace/${endpoint}?${query}`;

  try {
    const response = await fetch(url, {
      headers: { 'apiKey': apiKey }
    });

    const data = await response.json();

    // Cache 20s to reduce API calls
    res.setHeader('Cache-Control', 's-maxage=20, stale-while-revalidate=10');
    res.setHeader('Access-Control-Allow-Origin', '*');

    return res.status(200).json(data);
  } catch (err) {
    return res.status(502).json({ error: 'API request failed', message: err.message });
  }
}
