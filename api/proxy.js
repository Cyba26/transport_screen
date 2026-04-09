export default async function handler(req, res) {
  const { endpoint, ...params } = req.query;

  const allowed = ['stop-monitoring', 'general-message', 'sncf-departures'];
  if (!endpoint || !allowed.includes(endpoint)) {
    return res.status(400).json({ error: 'Invalid endpoint' });
  }

  res.setHeader('Cache-Control', 's-maxage=20, stale-while-revalidate=10');
  res.setHeader('Access-Control-Allow-Origin', '*');

  try {
    if (endpoint === 'sncf-departures') {
      const sncfKey = process.env.SNCF_API_KEY;
      if (!sncfKey) return res.status(500).json({ error: 'SNCF API key not configured' });

      const { stop_area_id, count = 20 } = params;
      const url = `https://api.sncf.com/v1/coverage/sncf/stop_areas/${stop_area_id}/departures?count=${count}`;
      const response = await fetch(url, {
        headers: { 'Authorization': 'Basic ' + Buffer.from(sncfKey + ':').toString('base64') }
      });
      const data = await response.json();
      return res.status(200).json(data);
    }

    const primKey = process.env.PRIM_API_KEY;
    if (!primKey) return res.status(500).json({ error: 'PRIM API key not configured' });

    const query = new URLSearchParams(params).toString();
    const url = `https://prim.iledefrance-mobilites.fr/marketplace/${endpoint}?${query}`;
    const response = await fetch(url, { headers: { 'apiKey': primKey } });
    const data = await response.json();
    return res.status(200).json(data);

  } catch (err) {
    return res.status(502).json({ error: 'API request failed', message: err.message });
  }
}
