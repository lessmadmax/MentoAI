import csv
from pathlib import Path

src = Path('src/main/resources/jobs.csv')
rows = []
with src.open(newline='', encoding='utf-8') as f:
    reader = csv.reader(f)
    rows = list(reader)

header = rows[0]
if len(header) < 5:
    header.append('keywords')
elif header[4].strip().lower() != 'keywords':
    if len(header) >= 5:
        header[4] = 'keywords'
    else:
        header.append('keywords')

clusters = {
    'software': [
        'software','development','web','app','frontend','backend','fullstack','api','programming','coding',
        'data','ai','machine learning','cloud','devops','security','network','database','sql','spring','react','node'
    ],
    'marketing': [
        'marketing','advertising','branding','digital','content','social','crm','sales','ecommerce','performance',
        'research','analytics','presentation','campaign','pr','communication','copywriting','viral','influencer','retention'
    ],
    'design': [
        'design','ui','ux','graphic','video','motion','branding','photoshop','illustrator','figma',
        'prototype','wireframe','typography','layout','color','visual','3d','render','storyboard','concept'
    ],
    'engineering': [
        'engineering','design','manufacturing','production','quality','safety','cad','process','simulation','plant',
        'mechanical','electrical','electronics','civil','architecture','materials','control','robot','construction','maintenance'
    ],
    'research': [
        'research','education','paper','analysis','experiment','literature','statistics','modeling','data','review',
        'presentation','seminar','lecture','workshop','study','design of experiment','validation','proposal','mentoring','evaluation'
    ],
    'finance': [
        'finance','accounting','tax','investment','risk','analysis','excel','report','audit','management accounting',
        'valuation','portfolio','budget','cost','law','corporate tax','statements','fund','insurance','derivatives'
    ],
    'business': [
        'business','planning','strategy','operations','project','organization','process','performance','okr','kpi',
        'problem solving','leadership','negotiation','coordination','resource','risk','schedule','report','analysis','execution'
    ],
    'general': [
        'professional','role','skill','experience','problem solving','collaboration','communication','analysis','planning',
        'organization','management','leadership','execution','creativity','learning','growth','quality','performance','goal'
    ],
}

def pick_cluster(jobclcd_nm, jobnm):
    text = (jobclcd_nm + ' ' + jobnm).lower()
    if any(k in text for k in ['software','it','dev','data','security','computer','network','programmer','developer']):
        return 'software'
    if any(k in text for k in ['marketing','advertising','promotion','branding','sales','commerce','planning','business']):
        return 'marketing'
    if any(k in text for k in ['design','art','media','video','graphic','broadcast']):
        return 'design'
    if any(k in text for k in ['construction','civil','mechanical','electrical','electronic','chemical','environment','manufacturing','production','plant']):
        return 'engineering'
    if any(k in text for k in ['research','teacher','professor','lecturer','education']):
        return 'research'
    if any(k in text for k in ['finance','bank','insurance','accounting','tax','investment','securities']):
        return 'finance'
    return 'general'

out_rows = [header]
for row in rows[1:]:
    while len(row) < 5:
        row.append('')
    jobclcd_nm = row[1] if len(row) > 1 else ''
    jobnm = row[3] if len(row) > 3 else ''
    cluster = pick_cluster(jobclcd_nm, jobnm)
    keywords = clusters.get(cluster, clusters['general'])
    row[4] = ';'.join(keywords)
    out_rows.append(row)

backup = src.with_suffix('.csv.bak')
if not backup.exists():
    backup.write_bytes(src.read_bytes())

with src.open('w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerows(out_rows)

print(f"Updated {src} with keywords column; backup saved to {backup}")