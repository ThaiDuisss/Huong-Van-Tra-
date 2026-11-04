"""Round-trip and golden presentation parity; SQLite fixtures only."""
from dataclasses import replace
from datetime import date, datetime, timezone
from io import BytesIO
from zipfile import ZipFile
import unittest

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter
from openpyxl.utils.cell import range_boundaries

from app.service.business_report_workbook import build_workbook as operational_workbook
from app.service.business_report_excel import WIDTHS as OPERATIONAL_WIDTHS, TEA, DATE_FORMAT
from offline_benchmark.common.manifest import build_manifest
from offline_benchmark.evaluation.runner import evaluate, freeze_predictions
from offline_benchmark.evaluation.v1_state import build_opportunities
from offline_benchmark.reporting.evaluation_workbook import (
    HEADERS, MARKER, SHEETS, WIDTHS, UNKNOWN, PERCENT_FORMAT, SOURCE_NOTE, CALCULATION_NOTE, LIMITATION_NOTE, display_identifiers, match_rate, export_workbook, verify_workbook,
)
from offline_benchmark.tests.test_offline_evaluation import fixture
from tests.test_business_reports import DbFixture, NOW, order, snapshot


def built(products=None):
    data,catalog,splits=fixture(); catalog=products or catalog
    frozen=freeze_predictions(build_opportunities(splits),catalog)
    result=evaluate(frozen,splits,catalog)
    content=export_workbook(result,catalog,build_manifest(data,splits),datetime(2026,10,21,9,tzinfo=timezone.utc),
                            (date(2026,1,1),date(2026,1,11)))
    return content,result


def table_rows(ws,name):
    a,b,c,d=range_boundaries(ws.tables[name].ref)
    return list(ws.iter_rows(min_row=b+1,max_row=d,min_col=a,max_col=c,values_only=True))


class OfflineWorkbookTests(unittest.TestCase):
    def test_real_data_four_sheets_kpis_and_product_statistics_reconcile(self):
        content,result=built(); verification=verify_workbook(content,result)
        self.assertEqual(verification['opportunities'],2)
        wb=load_workbook(BytesIO(content))
        self.assertEqual(wb.sheetnames,SHEETS)
        self.assertEqual(wb.worksheets[0]['B6'].value,result.metrics['total_matched_opportunity_product_pairs'])
        self.assertEqual(len(table_rows(wb.worksheets[1],'Customers')),2)
        self.assertEqual(len(table_rows(wb.worksheets[3],'Matches')),2)
        for row in table_rows(wb.worksheets[2],'Products'):
            self.assertEqual(row[7],UNKNOWN)
        with ZipFile(BytesIO(content)) as archive:
            self.assertIsNone(archive.testzip())

    def test_headers_freeze_filters_widths_and_gridlines(self):
        content,result=built(); wb=load_workbook(BytesIO(content))
        for index,ws in enumerate(wb):
            header=9 if index==0 else 3
            self.assertEqual([c.value for c in ws[header]],HEADERS[index])
            self.assertEqual(ws.freeze_panes,None if index==0 else 'A4')
            self.assertTrue(ws.sheet_view.showGridLines)
            self.assertEqual([ws.column_dimensions[get_column_letter(c)].width for c in range(1,len(WIDTHS[index])+1)],list(WIDTHS[index]))
            table=next(iter(ws.tables.values()))
            self.assertEqual(table.autoFilter.ref,table.ref)
            self.assertIsNone(ws.auto_filter.ref)
            self.assertFalse(table.tableStyleInfo.showRowStripes)
            self.assertEqual(ws.print_title_rows,f'$'+str(header)+':$'+str(header))
            self.assertIsNotNone(ws.print_area)
            self.assertEqual(ws['A1'].font.name,'Calibri')
            self.assertEqual(ws.cell(header,1).fill.fgColor.rgb[-6:],TEA)

    def test_naive_cutoff_is_only_in_comment_and_real_period_dates_remain_dates(self):
        content,result=built(); wb=load_workbook(BytesIO(content))
        for ws in wb:
            self.assertEqual(ws['A2'].number_format,'"Từ ngày: "'+DATE_FORMAT)
            self.assertEqual(ws['A2'].data_type,'d')
            self.assertIn(MARKER,ws['A1'].value)
            self.assertIn(SOURCE_NOTE,ws['A1'].comment.text)
        for row in (4,5):
            self.assertEqual(wb.worksheets[1].cell(row,2).value,UNKNOWN)
            self.assertIn('Không có ngày gợi ý thực tế',wb.worksheets[1].cell(row,2).comment.text)
        self.assertEqual(wb.properties.title,'BÁO CÁO HIỆU QUẢ GỢI Ý SẢN PHẨM')
        self.assertNotIn('UNFILTERED_BENCHMARK',wb.properties.description)
        self.assertNotIn('Mua lại',wb.worksheets[1]['C4'].value)

    def test_no_raw_customer_ids_or_operational_statuses_anywhere_in_package(self):
        content,result=built()
        with ZipFile(BytesIO(content)) as archive:
            text=''.join(archive.read(n).decode('utf-8') for n in archive.namelist() if n.endswith('.xml'))
        for forbidden in ('sensitive-customer-A','sensitive-customer-B','NO_PURCHASE','SUPERSEDED','NOT_YET_EVALUATED'):
            self.assertNotIn(forbidden,text)
        self.assertIn('GY-001',text)
        for forbidden in ('OFF-','UNFILTERED_BENCHMARK','HYBRID','TF-IDF','Precision@5','Ground truth','trùng Test'):
            self.assertNotIn(forbidden,text)

    def test_untrusted_names_are_literals_and_long_names_do_not_duplicate_counts(self):
        _,products,_=fixture()
        names=('=HYPERLINK("https://invalid","x")', 'TRÀ HƯƠNG VÂN '+('W'*30+' ')*130)
        changed=tuple(replace(p,name=names[n%2]) for n,p in enumerate(products))
        content,result=built(changed)
        verify_workbook(content,result)
        wb=load_workbook(BytesIO(content))
        self.assertFalse(any(c.data_type=='f' for ws in wb for row in ws for c in row))
        self.assertTrue(all(d.height<=409.5 for ws in wb for d in ws.row_dimensions.values()))
        self.assertEqual(sum(r[2] or 0 for r in table_rows(wb.worksheets[3],'Matches')),result.metrics['total_recommendation_items'])

    def test_empty_report_keeps_four_sheets_headers_zero_kpis_and_na_quantity(self):
        from offline_benchmark.evaluation.contract import FrozenPredictions
        _,products,_=fixture()
        result=evaluate(FrozenPredictions.freeze(()),(),products)
        content=export_workbook(result,products,{'split_sha256':'0'*64},datetime.now(timezone.utc),(date(2026,1,1),date(2026,1,1)))
        verify_workbook(content,result)
        wb=load_workbook(BytesIO(content))
        self.assertEqual([wb.worksheets[0][c].value for c in ('B4','B5','B6','B7')],[0,0,0,UNKNOWN])
        self.assertTrue(all(not ws.tables for ws in wb))


class OperationalPresentationParityTests(DbFixture,unittest.TestCase):
    def test_shared_operational_helpers_remain_unchanged_for_offline_extension(self):
        self.db.add_all([snapshot(),order()]); self.db.commit()
        operational,_=operational_workbook(self.db,date(2026,10,11),date(2026,10,12),NOW)
        baseline=load_workbook(BytesIO(operational)); content,result=built(); offline=load_workbook(BytesIO(content))
        self.assertEqual(baseline.sheetnames,offline.sheetnames)
        operational_headers=[
            ["STT","Sản phẩm","Lượt AI gợi ý","Số ly bán","Lượt trùng mua"],
            ["Mã KH","Ngày gợi ý","AI gợi ý","Khách mua sau đó","Món trùng"],
            ["STT","Sản phẩm","Số khách được gợi ý","Lượt gợi ý","Số đơn có mua","Số ly bán"],
            ["Mã lần gợi ý","Mã KH","Số món gợi ý","Số món khách mua","Số món trùng","Kết quả"]]
        for index,(before,after) in enumerate(zip(baseline,offline)):
            header=9 if index==0 else 3
            self.assertEqual(before.freeze_panes,after.freeze_panes)
            self.assertEqual(before.sheet_view.showGridLines,after.sheet_view.showGridLines)
            self.assertEqual(str(before.page_setup),str(after.page_setup))
            self.assertEqual(str(before.page_margins),str(after.page_margins))
            self.assertEqual(before.print_title_rows,after.print_title_rows)
            for coordinate in ('A1','A2','C2'):
                self.assertEqual(before[coordinate]._style,after[coordinate]._style)
            self.assertEqual(before.cell(header,1)._style,after.cell(header,1)._style)
            self.assertEqual(after.cell(header,1).fill.fgColor.rgb[-6:],TEA)
            self.assertEqual([c.value for c in before[header]],operational_headers[index])
            self.assertEqual([before.column_dimensions[get_column_letter(c)].width for c in range(1,len(OPERATIONAL_WIDTHS[index])+1)],list(OPERATIONAL_WIDTHS[index]))


class BusinessPercentageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from offline_benchmark.evaluation.contract import FrozenPredictions, RankedItem
        cls.data,cls.products,cls.splits=fixture()
        original=freeze_predictions(build_opportunities(cls.splits),cls.products)
        first=replace(original.predictions[0],items=tuple(RankedItem(product_id=p,rank=n,score=1/n) for n,p in enumerate(('p3','p0','p1','p2','p5'),1)))
        second=replace(original.predictions[1],items=(RankedItem(product_id='p3',rank=1,score=1.0),))
        cls.result=evaluate(FrozenPredictions.freeze((first,second)),cls.splits,cls.products)

    def workbook(self,result=None):
        result=result or self.result
        content=export_workbook(result,self.products,build_manifest(self.data,self.splits),datetime.now(timezone.utc),(date(2026,1,1),date(2026,1,11)))
        verify_workbook(content,result)
        return load_workbook(BytesIO(content))

    def test_overall_is_weighted_by_recommendation_count_not_mean_of_opportunity_rates(self):
        wb=self.workbook()
        self.assertAlmostEqual(wb.worksheets[0]['B7'].value,2/6)
        comparison=table_rows(wb.worksheets[3],'Matches')
        self.assertEqual([r[5] for r in comparison],[.2,1])
        self.assertNotAlmostEqual(wb.worksheets[0]['B7'].value,sum(r[5] for r in comparison)/2)
        for row in (4,5): self.assertEqual(wb.worksheets[3].cell(row,6).number_format,PERCENT_FORMAT)

    def test_product_rates_use_matching_pairs_not_order_count_and_zero_boundaries(self):
        wb=self.workbook(); rows={r[1]:r for r in table_rows(wb.worksheets[2],'Products')}
        self.assertEqual(rows['Trà 3'][3:7],(2,2,2,1))
        self.assertEqual(rows['Trà 0'][5:7],(0,0))
        self.assertEqual(rows['Trà 4'][3],0)
        self.assertEqual(rows['Trà 4'][6],'Chưa được gợi ý')
        self.assertEqual(sum(r[5] for r in rows.values()),wb.worksheets[0]['B6'].value)
        self.assertTrue(all(r[7]==UNKNOWN for r in rows.values()))

    def test_many_test_orders_with_same_product_do_not_multiply_matching_pairs(self):
        first=self.result.rows[0]
        modified=replace(first,test_order_products=first.test_order_products+(('extra-order',('p3',)),))
        from offline_benchmark.evaluation.contract import EvaluationResult
        changed=EvaluationResult(self.result.frozen,(modified,)+self.result.rows[1:],self.result.metrics_json)
        wb=self.workbook(changed)
        row=next(r for r in table_rows(wb.worksheets[2],'Products') if r[1]=='Trà 3')
        self.assertEqual(row[4:7],(3,2,1))

    def test_zero_denominator_is_unknown_and_valid_zero_is_numeric(self):
        from offline_benchmark.evaluation.contract import FrozenPredictions
        predictions=tuple(replace(p,items=()) for p in self.result.frozen.predictions)
        empty=evaluate(FrozenPredictions.freeze(predictions),self.splits,self.products)
        wb=self.workbook(empty)
        self.assertEqual(wb.worksheets[0]['B7'].value,UNKNOWN)
        self.assertTrue(all(r[5]=='Không có gợi ý' for r in table_rows(wb.worksheets[3],'Matches')))
        self.assertIsNone(match_rate(0,0)); self.assertEqual(match_rate(0,5),0)
        for hit,count in ((1,0),(6,5),(-1,5),(0,-1)):
            with self.assertRaises(ValueError): match_rate(hit,count)

    def test_short_ids_consistent_and_internal_mapping_stable_without_mutating_fingerprint(self):
        before=self.result.frozen.sha256; wb=self.workbook()
        a=display_identifiers(self.result); self.assertEqual(a,display_identifiers(self.result))
        self.assertEqual([r[0] for r in table_rows(wb.worksheets[1],'Customers')],['KH-001','KH-002'])
        self.assertEqual([r[:2] for r in table_rows(wb.worksheets[3],'Matches')],[('GY-001','KH-001'),('GY-002','KH-002')])
        self.assertEqual(a[0]['customer_reference'],self.result.rows[0].prediction.customer)
        self.assertEqual(self.result.frozen.sha256,before)

    def test_missing_ground_truth_does_not_claim_zero_percent(self):
        from offline_benchmark.evaluation.contract import FrozenPredictions
        splits=tuple(replace(s,test=()) for s in self.splits)
        missing=evaluate(FrozenPredictions.freeze(self.result.frozen.predictions),splits,self.products)
        wb=self.workbook(missing)
        self.assertTrue(all(r[5]=='Chưa có dữ liệu đối chiếu' for r in table_rows(wb.worksheets[3],'Matches')))

    def test_visible_package_contains_no_long_internal_references(self):
        content=export_workbook(self.result,self.products,build_manifest(self.data,self.splits),datetime.now(timezone.utc),(date(2026,1,1),date(2026,1,11)))
        with ZipFile(BytesIO(content)) as archive:
            text=''.join(archive.read(n).decode('utf-8') for n in archive.namelist() if n.endswith('.xml'))
        for p in self.result.frozen.predictions:
            for internal in (p.customer,p.opportunity_ref,p.state_sha256,p.cutoff): self.assertNotIn(internal,text)

    def test_serialized_percentage_tampering_is_detected(self):
        for sheet,coordinate in ((0,'B7'),(1,'F4'),(2,'G4'),(3,'F4')):
            wb=self.workbook(); wb.worksheets[sheet][coordinate]=1.2
            output=BytesIO(); wb.save(output)
            with self.assertRaises(ValueError): verify_workbook(output.getvalue(),self.result)


class CustomerMatchRateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data,cls.products,cls.splits=fixture()
        cls.frozen=freeze_predictions(build_opportunities(cls.splits),cls.products)

    def result(self,truth=('p0','p1'),items=('p0','p1','p2','p3','p4')):
        from offline_benchmark.evaluation.contract import FrozenPredictions, RankedItem
        original=self.splits[0]
        events=tuple(replace(original.test[0],event_id=f'customer-rate-{n}',product_id=p) for n,p in enumerate(truth))
        split=replace(original,test=events)
        prediction=replace(self.frozen.predictions[0],items=tuple(RankedItem(rank=n,product_id=p,score=1/n) for n,p in enumerate(items,1)))
        return evaluate(FrozenPredictions.freeze((prediction,)),(split,),self.products)

    def content(self,result,products=None,period_notes=()):
        return export_workbook(result,products or self.products,build_manifest(self.data,self.splits),
            datetime.now(timezone.utc),(date(2026,1,1),date(2026,1,11)),period_notes=period_notes)

    def test_customer_percentage_column_numeric_zero_twenty_forty_and_hundred(self):
        for truth,expected in ((('p5',),0),(('p0',),.2),(('p0','p1'),.4),(('p0','p1','p2','p3','p4'),1)):
            with self.subTest(expected=expected):
                result=self.result(truth); content=self.content(result); verify_workbook(content,result)
                wb=load_workbook(BytesIO(content)); ws=wb.worksheets[1]
                self.assertEqual(ws['F3'].value,'Tỷ lệ gợi ý trùng mua (%)')
                self.assertEqual(ws['F4'].value,expected)
                self.assertEqual(ws['F4'].data_type,'n')
                self.assertEqual(ws['F4'].number_format,'0.00%')
                self.assertEqual(ws['F4'].value,wb.worksheets[3]['F4'].value)
                self.assertIn('F',str(ws.print_area))
                self.assertEqual(ws.tables['Customers'].autoFilter.ref,ws.tables['Customers'].ref)

    def test_customer_no_recommendation_and_missing_purchase_are_not_false_zero(self):
        for result,expected in ((self.result(items=()),'Không có gợi ý'),
                                (self.result(truth=()),'Chưa có dữ liệu đối chiếu')):
            content=self.content(result); verify_workbook(content,result)
            wb=load_workbook(BytesIO(content))
            self.assertEqual(wb.worksheets[1]['F4'].value,expected)
            self.assertEqual(wb.worksheets[1]['F4'].value,wb.worksheets[3]['F4'].value)

    def test_customer_continuation_percentage_only_occurs_on_main_row(self):
        result=self.result()
        products=tuple(replace(p,name=p.name+' '+('tên sản phẩm dài '*500)) for p in self.products)
        content=self.content(result,products); verify_workbook(content,result)
        wb=load_workbook(BytesIO(content)); ws=wb.worksheets[1]
        rows=table_rows(ws,'Customers')
        self.assertGreater(len(rows),1)
        self.assertEqual(rows[0][5],.4)
        self.assertTrue(all(r[5] is None for r in rows[1:]))
        self.assertTrue(all(r[0]=='KH-001' and r[1]==UNKNOWN for r in rows))
        self.assertEqual(len(table_rows(wb.worksheets[3],'Matches')),1)
        self.assertEqual(sum(r[2] for r in table_rows(wb.worksheets[3],'Matches')),5)
        self.assertTrue(ws.tables['Customers'].ref.startswith('A3:F'))
        self.assertTrue(all(d.height<=409.5 for d in ws.row_dimensions.values()))

    def test_reconciliation_joins_stable_opportunity_not_physical_row(self):
        result=evaluate(self.frozen,self.splits,self.products)
        content=self.content(result); wb=load_workbook(BytesIO(content)); ws=wb.worksheets[3]
        first=[c.value for c in ws[4]]; second=[c.value for c in ws[5]]
        for n,(a,b) in enumerate(zip(first,second),1):
            ws.cell(4,n).value=b; ws.cell(5,n).value=a
        output=BytesIO(); wb.save(output)
        verify_workbook(output.getvalue(),result)
        comparison={r[1]:r[5] for r in table_rows(ws,'Matches')}
        for row in table_rows(wb.worksheets[1],'Customers'):
            self.assertEqual(row[5],comparison[row[0]])

    def test_duplicate_or_wrong_customer_percentage_is_rejected(self):
        result=self.result()
        for mutation in ('wrong','duplicate'):
            wb=load_workbook(BytesIO(self.content(result)))
            ws=wb.worksheets[1]
            if mutation=='wrong': ws['F4']=.2
            else:
                for n,c in enumerate(list(ws[4]),1): ws.cell(5,n).value=c.value
                ws.tables['Customers'].ref='A3:F5'
            output=BytesIO(); wb.save(output)
            with self.assertRaises(ValueError): verify_workbook(output.getvalue(),result)

    def test_main_notes_are_three_short_business_notes_and_technical_metadata_stays_out(self):
        result=self.result(); content=self.content(result,period_notes=('Source-calendar boundary; Cutoff Hash Artifact Snapshot Model protocol',))
        wb=load_workbook(BytesIO(content)); ws=wb.worksheets[0]
        heading=next(c.row for row in ws for c in row if c.value=='GHI CHÚ VÀ PHƯƠNG PHÁP THỐNG KÊ')
        notes=[ws.cell(n,1).value for n in range(heading+1,ws.max_row+1)]
        self.assertEqual(notes,[SOURCE_NOTE,CALCULATION_NOTE,LIMITATION_NOTE])
        self.assertIn('chưa có bằng chứng khách đã xem',notes[-1])
        with ZipFile(BytesIO(content)) as package:
            text=''.join(package.read(n).decode('utf-8') for n in package.namelist() if n.endswith('.xml'))
        for term in ('Source-calendar boundary','Cutoff','Hash','Artifact','Snapshot','Model protocol'):
            self.assertNotIn(term,text)
