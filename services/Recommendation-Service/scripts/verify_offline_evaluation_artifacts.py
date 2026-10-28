"""Independent read-back verification and preview data for an external offline run."""
import argparse
from collections import Counter
from dataclasses import asdict
from datetime import datetime
import hashlib
import json
from pathlib import Path
import sys

SERVICE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(SERVICE))


def verify(directory):
    from openpyxl import load_workbook
    from openpyxl.utils import get_column_letter
    from offline_benchmark.evaluation.contract import FrozenPredictions,Prediction,RankedItem
    from offline_benchmark.evaluation.output import protected_hashes,report_helper,json_bytes,sha256
    from offline_benchmark.evaluation.runner import verify_archive,evaluate
    from offline_benchmark.evaluation.v1_state import CanonicalTrain,build_opportunities
    from offline_benchmark.reporting.evaluation_workbook import verify_workbook
    directory=report_helper().verify_destination(Path(directory))
    manifest=json.loads((directory/'manifest.json').read_text(encoding='utf-8'))
    for name,expected in manifest['artifact_sha256'].items():
        if sha256((directory/name).read_bytes())!=expected: raise ValueError('Artifact checksum mismatch: '+name)
    for line in (directory/'checksums.sha256').read_text().splitlines():
        expected,name=line.split('  ',1)
        if Path(name).name!=name or sha256((directory/name).read_bytes())!=expected: raise ValueError('Checksum list mismatch')
    if protected_hashes()!=manifest['protected_file_sha256']: raise ValueError('Protected source/artifacts changed')
    for name,expected in manifest['source_file_sha256'].items():
        if sha256((SERVICE/name).read_bytes())!=expected: raise ValueError('Evaluation code differs from executed run: '+name)
    dataset=verify_archive(manifest['source_path'],SERVICE/'offline_benchmark/outputs/split_manifest.json',manifest['source_archive_sha256'])
    saved=json.loads((directory/'predictions.json').read_text(encoding='utf-8'))
    predictions=tuple(Prediction(**{**p,'items':tuple(RankedItem(**i) for i in p['items']),
                                     'candidate_ids':tuple(p['candidate_ids'])}) for p in saved['predictions'])
    frozen=FrozenPredictions(predictions,saved['prediction_sha256']); frozen.verify()
    result=evaluate(frozen,dataset.splits,dataset.products)
    if result.metrics!=json.loads((directory/'metrics.json').read_text(encoding='utf-8')): raise ValueError('Independent metrics differ')
    if json.loads(json.dumps([asdict(r) for r in result.rows]))!=json.loads((directory/'evaluation.json').read_text(encoding='utf-8')):
        raise ValueError('Saved evaluation rows differ')
    plan=build_opportunities(dataset.splits); train=CanonicalTrain.from_opportunities(plan)
    encoded={e.event_id:json.dumps(asdict(e)|{'event_time':e.event_time.isoformat()},sort_keys=True,
             ensure_ascii=False,separators=(',',':')).encode('utf-8') for o in train.orders for e in o.events}
    test_ids={e.event_id for s in dataset.splits for e in s.test}
    for opportunity,prediction in zip(plan,predictions):
        orders,future,spanning=train.at(opportunity.cutoff)
        ids=sorted(e.event_id for o in orders for e in o.events)
        checksum=hashlib.sha256(b'['+b','.join(encoded[i] for i in ids)+b']').hexdigest()
        if (test_ids.intersection(ids) or checksum!=prediction.state_events_sha256
                or len(ids)!=prediction.state_event_count or len(orders)!=prediction.state_order_count
                or future!=prediction.future_orders_excluded or spanning!=prediction.spanning_orders_excluded):
            raise ValueError('Independent causal state reconstruction failed')
    workbook=directory/'AI_Offline_Evaluation_HuongVanTra_TrainTest.xlsx'
    verification=verify_workbook(workbook.read_bytes(),result)
    wb=load_workbook(workbook)
    raw_customers={s.customer_id for s in dataset.splits}
    for ws in wb:
        for row in ws:
            for cell in row:
                if isinstance(cell.value,str) and cell.value in raw_customers:
                    raise ValueError('Raw customer ID exposed in workbook')
    layouts=[]
    for index,ws in enumerate(wb,1):
        preview_end=min(ws.max_row,14 if index==1 else 9)
        widths=[round(ws.column_dimensions[get_column_letter(c)].width*7+5) for c in range(1,ws.max_column+1)]
        heights=[round((ws.row_dimensions[r].height or 15)*96/72) for r in range(1,preview_end+1)]
        cells=[]
        for row in ws.iter_rows(max_row=preview_end):
            for cell in row:
                if cell.value is None: continue
                text=str(cell.value)
                if isinstance(cell.value,datetime):
                    text=cell.value.strftime('%d/%m/%Y')
                    if cell.row==2: text=('Từ ngày: ' if cell.column==1 else 'Đến ngày: ')+text
                merge=next((m for m in ws.merged_cells.ranges if cell.coordinate in m),None)
                cells.append(dict(text=text,row=cell.row,col=cell.column,endCol=merge.max_col if merge else cell.column,
                    fontSize=cell.font.sz or 11,bold=bool(cell.font.b),
                    color=cell.font.color.rgb[-6:] if cell.font.color and cell.font.color.type=='rgb' else '000000',
                    fill=cell.fill.fgColor.rgb[-6:] if cell.fill.patternType=='solid' else 'FFFFFF',
                    align=cell.alignment.horizontal or 'left'))
        layouts.append(dict(name='OFFLINE-sheet-'+str(index),widths=widths,heights=heights,cells=cells))
    verification.update(independent_state_checks=len(predictions),independent_metric_recomputation='PASS',
                        original_manifest_exact_match=True,protected_files_unchanged=True,
                        source_hashes_match_run=True,raw_customer_ids_absent=True,
                        routes=dict(Counter(p.route for p in predictions)),
                        fallbacks=dict(Counter(p.fallback_reason for p in predictions if p.fallback_reason)),
                        prediction_sha256=frozen.sha256,workbook_sha256=sha256(workbook.read_bytes()),
                        preview_limit='Approximate System.Drawing/Calibri preview; not native Excel rendering')
    for name,value in [('implementation-verification.json',verification),('visual-layout.json',layouts)]:
        with (directory/name).open('xb') as output: output.write(json_bytes(value))
    return verification


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__,allow_abbrev=False)
    parser.add_argument('directory',type=Path)
    print(json.dumps(verify(parser.parse_args().directory),ensure_ascii=False,indent=2))
