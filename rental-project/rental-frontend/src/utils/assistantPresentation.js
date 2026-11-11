import MarkdownIt from 'markdown-it'
const md=new MarkdownIt({html:false,linkify:true,breaks:true})
export function localDocumentUrl(value,origin='http://127.0.0.1:5173'){
 try{const url=new URL(value,origin),base=new URL(origin);if(!['http:','https:'].includes(url.protocol)||!['localhost','127.0.0.1',base.hostname].includes(url.hostname))return ''
 if(!/^\/api\/file\/download\/(?:[A-Za-z0-9_-]+\/){0,4}[A-Za-z0-9_.%-]+\.(pdf|docx)$/i.test(url.pathname))return ''
 return url.pathname
 }catch{return ''}
}
const linkOpen=md.renderer.rules.link_open||((tokens,index,options,env,self)=>self.renderToken(tokens,index,options))
md.renderer.rules.link_open=(tokens,index,options,env,self)=>{const token=tokens[index],href=token.attrGet('href')||'',doc=localDocumentUrl(href,env?.origin);if(doc)token.attrSet('href',doc);if(/^https?:/i.test(href)&&!doc){token.attrSet('target','_blank');token.attrSet('rel','noopener noreferrer')}return linkOpen(tokens,index,options,env,self)}
md.renderer.rules.image=(tokens,index)=>md.utils.escapeHtml(tokens[index].content||'图片')
export function renderAssistantMarkdown(text,origin){return md.render(String(text||''),{origin})}
export function documentActions(message,origin){
 const results=(message.actions||[]).filter(a=>['generate_pdf','generate_docx'].includes(a.type)&&localDocumentUrl(a.result?.downloadUrl,origin)).map(a=>({...a,result:{...a.result,downloadUrl:localDocumentUrl(a.result.downloadUrl,origin)}}))
 for(const match of String(message.content||'').matchAll(/(?:https?:\/\/[^\s<>"\)]+)?\/api\/file\/download\/[^\s<>"\)]+\.(pdf|docx)/gi)){
  const url=localDocumentUrl(match[0],origin);if(!url||results.some(a=>a.result.downloadUrl===url))continue
  results.push({id:url,type:match[1].toLowerCase()==='pdf'?'generate_pdf':'generate_docx',result:{downloadUrl:url,filename:decodeURIComponent(url.split('/').pop()),fileType:match[1].toLowerCase(),title:'生成的文档'}})
 }
 return results
}
export function visibleMessageText(message,origin){let text=String(message.content||'');for(const action of documentActions(message,origin)){const path=action.result.downloadUrl;const escaped=path.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');text=text.replace(new RegExp('(?:https?:\\/\\/[^\\s<>"\\)]+)?'+escaped,'g'),'');}return text.replace(/^\s*(?:下载链接|下载地址|文件链接)[:：]?\s*$/gm,'').trim()}
export function applyAssistantFrame(message,frame){
 const lines=frame.split('\n'),event=lines.find(l=>l.startsWith('event:'))?.slice(6).trim(),data=lines.filter(l=>l.startsWith('data:')).map(l=>l.slice(5).replace(/^ /,'')).join('\n');if(!data)return
 let payload;try{payload=JSON.parse(data)}catch{return}
 if(event==='text')message.content+=(payload.text||'')
 else if(event==='status')message.progress=payload.message||''
 else if(event==='action'&&payload.action){if(!(message.actions||[]).some(a=>a.id===payload.action.id))message.actions.push(payload.action);message.progress='正在结合结果整理回答…'}
 else if(event==='done'){message.actions=payload.actions||message.actions;message.privateDrafts=payload.privateDrafts||[];message.streaming=false;message.progress=''}
 else if(event==='error'){message.error=payload.message||'回复中断，请重试';message.streaming=false;message.progress=''}
}
