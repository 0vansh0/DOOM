import { it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FeedbackDesk } from './FeedbackDesk';

it('previews a selected report before explicit consent enables submission', async () => {
  const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({data:[]})));
  try {
    render(<FeedbackDesk token="fixture" review={false}/>);
    const submit=screen.getByRole('button',{name:'Submit reviewed report'});
    expect(submit).toBeDisabled();
    const file=new File(['{}'],'report.json',{type:'application/json'});
    Object.defineProperty(file,'text',{value:async()=>'{"title":"Local fixture"}'});
    fireEvent.change(screen.getByLabelText(/Local report.json/),{target:{files:[file]}});
    await screen.findByText('{"title":"Local fixture"}');
    expect(submit).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox'));
    await waitFor(()=>expect(submit).toBeEnabled());
    expect(fetchMock.mock.calls.every(([,options])=>!options?.method||options.method==='GET')).toBe(true);
  } finally { fetchMock.mockRestore(); }
});

it('shows reviewer report details and persists triage through the protected API', async () => {
  const report={id:'00000000-0000-4000-8000-000000000001',report:{category:'Bug',title:'Ring missing',description:'Tested report'},priority:'P2',status:'open',component:'',reproduction:'unverified',regression_test:'',resolution:'',assignee_id:null,created_at:'2026-01-01T00:00:00.000Z',updated_at:'2026-01-01T00:00:00.000Z'};
  const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(async (_input,options)=>{
    if(options?.method==='POST')return new Response(JSON.stringify({data:{...report,priority:'P1',status:'in-progress',updated_at:'2026-02-01T00:00:00.000Z'}}));
    if(String(_input).includes('/v1/moderation/reviewers'))return new Response(JSON.stringify({data:[{id:'reviewer-id',email:'reviewer@example.test',role:'REVIEWER'}]}));
    return new Response(JSON.stringify({data:[report]}));
  });
  try {
    const user=userEvent.setup();
    render(<FeedbackDesk token="reviewer-token" review/>);
    expect(await screen.findByRole('heading',{name:'Ring missing'})).toBeInTheDocument();
    expect(screen.getByText(/Created .*Updated/)).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('priority'),'P1');
    await user.selectOptions(screen.getByLabelText('status'),'in-progress');
    await user.selectOptions(screen.getByLabelText('reproduction'),'reproduced');
    await user.type(screen.getByLabelText('component'),'overlay');
    await user.type(screen.getByLabelText('regression test'),'FeedbackDesk test');
    await user.type(screen.getByLabelText('resolution'),'Confirmed triage update');
    await user.selectOptions(screen.getByLabelText('owner'),'reviewer-id');
    await user.click(screen.getByRole('button',{name:'Save triage'}));
    expect(await screen.findByText('Triage saved.')).toBeInTheDocument();
    const call=fetchMock.mock.calls.find(([,options])=>options?.method==='POST');
    expect(call?.[0]).toContain('/v1/moderation/feedback/00000000-0000-4000-8000-000000000001');
    expect(call?.[1]?.headers).toBeInstanceOf(Headers);
    expect((call?.[1]?.headers as Headers).get('authorization')).toBe('Bearer reviewer-token');
    expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({priority:'P1',status:'in-progress',reproduction:'reproduced',assignee_id:'reviewer-id'});
  } finally { fetchMock.mockRestore(); }
});
