import request from './index'
export const messagesApi={
 inbox:()=>request.get('/messages'), unread:()=>request.get('/messages/unread'), start:houseId=>request.post('/messages/start',{houseId}),
 history:(id,params={})=>request.get(`/messages/${id}`,{params}), read:(id,through)=>request.post(`/messages/${id}/read`,{through}),
 send:(id,content,requestId)=>request.post(`/messages/${id}/send`,{content,requestId}),
 draft:(id,instruction)=>request.post(`/messages/${id}/draft`,{instruction},{timeout:120000}),
 cancel:id=>request.post(`/messages/drafts/${id}/cancel`),
 pending:()=>request.get('/messages/drafts'),confirm:(id,content)=>request.post(`/messages/drafts/${id}/confirm`,{content})
}
