package br.gov.jfrj.siga.gc.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import br.com.caelum.vraptor.ioc.Component;
import br.gov.jfrj.siga.base.AplicacaoException;
import br.gov.jfrj.siga.cp.CpIdentidade;
import br.gov.jfrj.siga.dp.CpMarcador;
import br.gov.jfrj.siga.dp.CpOrgaoUsuario;
import br.gov.jfrj.siga.dp.CpTipoMarca;
import br.gov.jfrj.siga.dp.DpLotacao;
import br.gov.jfrj.siga.dp.DpPessoa;
import br.gov.jfrj.siga.gc.model.GcArquivo;
import br.gov.jfrj.siga.gc.model.GcInformacao;
import br.gov.jfrj.siga.gc.model.GcMarca;
import br.gov.jfrj.siga.gc.model.GcMovimentacao;
import br.gov.jfrj.siga.gc.model.GcTag;
import br.gov.jfrj.siga.gc.model.GcTipoMovimentacao;
import br.gov.jfrj.siga.gc.model.GcTipoTag;
import br.gov.jfrj.siga.model.Historico;
import br.gov.jfrj.siga.model.Objeto;
import br.gov.jfrj.siga.vraptor.SigaObjects;

@Component
public class GcBL {
	private static final long TEMPO_NOVIDADE = 7 * 24 * 60 * 60 * 1000L;

	private EntityManager em;
	private SigaObjects so;
	private Date dt;

	public GcBL(EntityManager em, SigaObjects so) {
		super();
		this.em = em;
		this.so = so;
	}

	private String simplificarString(String s) {
		if (s == null)
			return null;
		if (s.trim().length() == 0)
			return null;
		return s.trim();
	}

	public GcMovimentacao movimentar(GcInformacao inf, GcArquivo arqDuplicado,
			long id) throws Exception {
		GcMovimentacao mov = new GcMovimentacao();
		mov.tipo = GcTipoMovimentacao.AR.findById(id);
		if (mov.tipo == null)
			throw new Exception(
					"Não foi possível localizar um tipo de movimentacão com id="
							+ id);
		mov.arq = arqDuplicado;
		return movimentar(inf, mov);
	}

	public GcMovimentacao movimentar(GcInformacao inf, long idTipo,
			DpPessoa pessoa, DpLotacao lotacao, String descricao,
			String titulo, String conteudo, String classificacao,
			GcMovimentacao movRef, Date hisDtIni, byte[] anexo)
			throws Exception {

		GcMovimentacao mov = new GcMovimentacao();
		mov.tipo = GcTipoMovimentacao.AR.findById(idTipo);
		if (mov.tipo == null)
			throw new Exception(
					"Não foi possível localizar um tipo de movimentacão com id="
							+ idTipo);
		mov.pessoaAtendente = pessoa;
		mov.lotacaoAtendente = lotacao;
		mov.descricao = descricao;
		mov.movRef = movRef;
		mov.hisDtIni = hisDtIni;

		titulo = simplificarString(titulo);
		conteudo = simplificarString(conteudo);

		if (conteudo != null && conteudo.startsWith("<")) {
			String canonicalizado = new ProcessadorHtml().canonicalizarHtml(
					conteudo, false, true, true, true, true);
			conteudo = canonicalizado;
		}

		classificacao = simplificarString(classificacao);
		if (idTipo == GcTipoMovimentacao.TIPO_MOVIMENTACAO_ANEXAR_ARQUIVO) {
			GcArquivo arq = new GcArquivo();
			arq.titulo = titulo;
			arq.classificacao = null;
			arq.setConteudoBinario(anexo, arq.obterMimeType());
			mov.arq = arq;
		} else {
			if (titulo != null && conteudo != null) {
				GcArquivo arq = new GcArquivo();
				arq.titulo = titulo;
				arq.setConteudoTXT(conteudo);
				arq.classificacao = classificacao;
				mov.arq = arq;
			} else if (idTipo == GcTipoMovimentacao.TIPO_MOVIMENTACAO_EDICAO
					|| idTipo == GcTipoMovimentacao.TIPO_MOVIMENTACAO_CRIACAO) {
				// throw new
				// Exception("Não é permitido salvar uma informação com título, conteúdo e classificação vazios.");
				throw new AplicacaoException(
						"Não é permitido salvar uma informação com título ou conteúdo vazios.");
			}
		}

		return movimentar(inf, mov);
	}

	public GcMovimentacao movimentar(GcInformacao inf, GcMovimentacao mov)
			throws Exception {
		Date dt = dt();
		if (mov.hisDtIni == null) {
			mov.hisDtIni = dt;
		}
		mov.inf = inf;
		/*
		 * if (mov.isCanceladora()) { for (GcMovimentacao mv : inf.movs) { if
		 * (mv.id == mov.movRef.id) mv.movCanceladora = mov; } }
		 */

		if (inf.movs == null)
			inf.movs = new TreeSet<GcMovimentacao>();
		inf.movs.add(mov);
		return mov;
	}

	public Date dt() {
		if (this.dt == null)
			this.dt = so.dao().dt();
		return this.dt;
	}

	public GcInformacao gravar(GcInformacao inf, CpIdentidade idc,
			DpPessoa titular, DpLotacao lotaTitular) throws Exception {
		Date dt = dt();
		// dao().iniciarTransacao();
		// try {

		// Atualiza o campo arq, pois este não pode ser nulo
		if (inf.movs != null) {
			for (GcMovimentacao mov : inf.movs) {
				if (inf.arq == null)
					inf.arq = mov.arq;
			}
		}

		if (inf.hisDtIni == null)
			inf.hisDtIni = dt;
		if (inf.hisIdcIni == null)
			inf.hisIdcIni = idc;
		if (inf.movs != null) {
			for (GcMovimentacao mov : inf.movs) {
				if (mov.arq != null && mov.arq.id == 0)
					mov.arq.save();
				if (mov.hisIdcIni == null)
					mov.hisIdcIni = idc;
				if (mov.hisDtIni == null)
					mov.hisDtIni = dt;
				if (mov.pessoaTitular == null)
					mov.pessoaTitular = titular;
				if (mov.lotacaoTitular == null)
					mov.lotacaoTitular = lotaTitular;
				if (inf.id == 0)
					inf.save();
				mov.inf = inf;
				if (mov.movCanceladora != null)
					mov.movCanceladora.save();
				mov.save();
			}
		}
		atualizarInformacaoPorMovimentacoes(inf);
		atualizarTags(inf);
		inf.save();
		atualizarMarcas(inf);
		// dao().commitTransacao();
		// } catch (Exception e) {
		// dao().rollbackTransacao();
		// }
		return inf;
	}

	private void atualizarTags(GcInformacao inf) throws Exception {
		inf.tags = new TreeSet<GcTag>();
		if (inf.arq.classificacao != null) {
			String a[] = inf.arq.classificacao.split(",");
			inf.tags = buscarTags(a, false);
		}
	}

	public Set<GcTag> buscarTags(String[] a, boolean fValidos) throws Exception {
		Set<GcTag> set = new TreeSet<GcTag>();
		for (String s : a) {
			String categoria = null;
			String titulo = null;
			Long tipo = null;
			s = simplificarString(s);
			if (s != null) {
				if (s.startsWith("@") || s.startsWith("^")) {
					if (s.startsWith("@"))
						tipo = GcTipoTag.TIPO_TAG_CLASSIFICACAO;
					else
						tipo = GcTipoTag.TIPO_TAG_ANCORA;
					s = s.substring(1);
					String aa[] = s.split(":");
					if (aa.length > 2) {
						continue;
					} else if (aa.length == 2) {
						categoria = simplificarString(aa[0]);
						titulo = simplificarString(aa[1]);
					} else {
						titulo = simplificarString(aa[0]);
					}
				} else if (s.startsWith("#")) {
					s = s.substring(1);
					titulo = simplificarString(s);
					tipo = GcTipoTag.TIPO_TAG_HASHTAG;
				}
			}
			GcTag tag;
			if (categoria == null)
				tag = GcTag.AR.find(
						"tipo.id = ? and categoria is null and titulo = ?",
						tipo, titulo).first();
			else
				tag = GcTag.AR.find(
						"tipo.id = ? and categoria = ? and titulo = ?", tipo,
						categoria, titulo).first();
			if (tag == null && !fValidos) {
				tag = new GcTag((GcTipoTag) GcTipoTag.AR.findById(tipo),
						categoria, titulo);
			}
			if (tag != null) {
				set.add(tag);
			}
		}

		if (set.size() == 0)
			return null;
		return set;
	}

	public void atualizarInformacaoPorMovimentacoes(GcInformacao inf)
			throws AplicacaoException {
		if (inf.movs == null)
			return;

		ArrayList<GcMovimentacao> movs = new ArrayList<GcMovimentacao>(
				inf.movs.size());
		movs.addAll(inf.movs);
		Collections.reverse(movs);

		for (GcMovimentacao mov : movs) {
			Long t = mov.tipo.id;

			if (mov.isCancelada())
				continue;

			if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_CRIACAO)
				inf.hisDtIni = mov.hisDtIni;

			if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_FECHAMENTO)
				inf.elaboracaoFim = mov.hisDtIni;

			if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_CANCELAMENTO)
				inf.hisDtFim = mov.hisDtIni;

			if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_EDICAO) {
				if (inf.autor == null)
					// inf.autor = mov.pessoa;
					inf.autor = mov.pessoaTitular;
				if (inf.lotacao == null)
					// inf.lotacao = mov.lotacao;
					inf.lotacao = mov.lotacaoTitular;
				inf.arq = mov.arq;
			}
		}
		if (inf.elaboracaoFim != null && inf.ano == null) {
			inf.ano = dt().getYear() + 1900;
			Query qry = em()
					.createQuery(
							"select max(inf.numero) from GcInformacao inf where ano = :ano and ou.idOrgaoUsu = :ouid");
			qry.setParameter("ano", inf.ano);
			qry.setParameter("ouid", inf.ou.getIdOrgaoUsu());
			Integer i = (Integer) qry.getSingleResult();
			inf.numero = (i == null ? 0 : i) + 1;
		}
	}

	public void atualizarMarcas(GcInformacao inf) throws Exception {
		SortedSet<GcMarca> setA = new TreeSet<GcMarca>();
		if (inf.marcas != null) {
			// Excluir marcas duplicadas
			for (GcMarca m : inf.marcas) {
				if (setA.contains(m))
					m.delete();
				else
					setA.add(m);
			}
		}
		SortedSet<GcMarca> setB = calcularMarcadores(inf);
		Set<GcMarca> incluir = new TreeSet<GcMarca>();
		Set<GcMarca> excluir = new TreeSet<GcMarca>();

		encaixar(setA, setB, incluir, excluir);
		for (GcMarca i : incluir) {
			if (i.inf.marcas == null) {
				// i.inf.marcas = new TreeSet<GcMarca>();
				i.inf.marcas = new ArrayList<GcMarca>();
			}
			// i.salvar();
			i.inf = inf;
			// dao().salvar(i);
			i.save();
			i.inf.marcas.add(i);
		}
		for (GcMarca e : excluir) {
			if (e.inf.marcas == null) {
				// e.inf.marcas = new TreeSet<GcMarca>();
				e.inf.marcas = new ArrayList<GcMarca>();
			}
			e.inf.marcas.remove(e);
			e.delete();
		}

	}

	/**
	 * Executa algoritmo de comparação entre dois sets e preenche as listas:
	 * inserir, excluir e atualizar.
	 */
	private void encaixar(SortedSet<GcMarca> setA, SortedSet<GcMarca> setB,
			Set<GcMarca> incluir, Set<GcMarca> excluir) {
		Iterator<GcMarca> ia = setA.iterator();
		Iterator<GcMarca> ib = setB.iterator();

		GcMarca a = null;
		GcMarca b = null;

		if (ia.hasNext())
			a = ia.next();
		if (ib.hasNext())
			b = ib.next();
		while (a != null || b != null) {
			if ((a == null) || (b != null && a.compareTo(b) > 0)) {
				// Existe em setB, mas nao existe em setA
				incluir.add(b);
				if (ib.hasNext())
					b = ib.next();
				else
					b = null;

			} else if (b == null || (a != null && b.compareTo(a) > 0)) {
				// Existe em setA, mas nao existe em setB
				excluir.add(a);
				if (ia.hasNext())
					a = ia.next();
				else
					a = null;
			} else {

				if (a == null) {
					int i = 0;
				}
				// O registro existe nos dois
				// atualizar.add(new Par(b, a));
				if (ib.hasNext())
					b = ib.next();
				else
					b = null;
				if (ia.hasNext())
					a = ia.next();
				else
					a = null;
			}
		}
		ib = null;
		ia = null;
	}

	private void acrescentarMarca(SortedSet<GcMarca> set, GcInformacao inf,
			Long idMarcador, Date dtIni, Date dtFim, DpPessoa pess,
			DpLotacao lota) throws Exception {
		CpTipoMarca tipoMarca = CpTipoMarca.AR
				.findById(CpTipoMarca.TIPO_MARCA_SIGA_GC);
		GcMarca mar = new GcMarca();
		mar.setCpTipoMarca(tipoMarca);
		mar.inf = inf;
		mar.setCpMarcador((CpMarcador) CpMarcador.AR.findById(idMarcador));
		if (pess != null)
			mar.setDpPessoaIni(pess.getPessoaInicial());
		if (lota != null)
			mar.setDpLotacaoIni(lota.getLotacaoInicial());
		mar.setDtIniMarca(dtIni);
		mar.setDtFimMarca(dtFim);
		set.add(mar);
	}

	/**
	 * Calcula quais as marcas cada informação terá com base nas
	 * movimentações que foram feitas na informacao.
	 * 
	 * @param inf
	 */
	private SortedSet<GcMarca> calcularMarcadores(GcInformacao inf)
			throws Exception {
		SortedSet<GcMarca> set = new TreeSet<GcMarca>();

		if (inf.hisDtFim != null) {
			acrescentarMarca(set, inf, CpMarcador.MARCADOR_CANCELADO,
					inf.hisDtFim, null, inf.autor, inf.lotacao);
		} else {
			if (inf.elaboracaoFim == null) {
				acrescentarMarca(set, inf, CpMarcador.MARCADOR_EM_ELABORACAO,
						inf.hisDtIni, null, inf.autor, inf.lotacao);
			} else {
				acrescentarMarca(set, inf, CpMarcador.MARCADOR_ATIVO,
						inf.elaboracaoFim, null, inf.autor, inf.lotacao);
				acrescentarMarca(set, inf, CpMarcador.MARCADOR_NOVO,
						inf.elaboracaoFim, new Date(inf.hisDtIni.getTime()
								+ TEMPO_NOVIDADE), inf.autor, inf.lotacao);
			}
			if (inf.movs != null) {
				for (GcMovimentacao mov : inf.movs) {
					Long t = mov.tipo.id;

					if (mov.isCancelada())
						continue;

					if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_PEDIDO_DE_REVISAO)
						acrescentarMarca(set, inf, CpMarcador.MARCADOR_REVISAR,
								mov.hisDtIni, null, mov.pessoaAtendente,
								mov.lotacaoAtendente);

					if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_NOTIFICAR
							&& (mov.pessoaAtendente != null || mov.lotacaoAtendente != null)) {

						/*
						 * /*Edson: pode ser usado quando o grupo de e-mail
						 * notificado for do siga-gi if (mov.grupo != null) {
						 * Map<DpLotacao, List<DpPessoa>> mapa = mov
						 * .getLotasEPessoasDoGrupo(); for (DpLotacao l :
						 * mapa.keySet()) if (mapa.get(l).size() == 0) {
						 * acrescentarMarca(set, inf,
						 * CpMarcador.MARCADOR_TOMAR_CIENCIA, mov.hisDtIni,
						 * null, null, l); } else { for (DpPessoa p :
						 * mapa.get(l)) acrescentarMarca( set, inf,
						 * CpMarcador.MARCADOR_TOMAR_CIENCIA, mov.hisDtIni,
						 * null, p, l); } }
						 */
						acrescentarMarca(set, inf,
								CpMarcador.MARCADOR_TOMAR_CIENCIA,
								mov.hisDtIni, null, mov.pessoaAtendente,
								mov.lotacaoAtendente);
					}

					if (t == GcTipoMovimentacao.TIPO_MOVIMENTACAO_INTERESSADO)
						acrescentarMarca(set, inf,
								CpMarcador.MARCADOR_COMO_INTERESSADO,
								mov.hisDtIni, null, mov.pessoaTitular, null);
				}
			}
		}
		return set;
	}

	private boolean dtMesmoDia(Date dt1, Date dt2) {
		return dt1.getDate() == dt2.getDate()
				&& dt1.getMonth() == dt2.getMonth()
				&& dt1.getYear() == dt2.getYear();
	}

	public void logarVisita(GcInformacao informacao, CpIdentidade idc,
			DpPessoa titular, DpLotacao lotaTitular) throws Exception {
		Date dt = dt();
		for (GcMovimentacao mov : informacao.movs) {
			if (mov.isCancelada())
				continue;
			if (mov.tipo.id == GcTipoMovimentacao.TIPO_MOVIMENTACAO_VISITA
					&& titular.equivale(mov.pessoaTitular)
					// && idc.getDpPessoa().equivale(mov.pessoa)
					&& dtMesmoDia(dt, mov.hisDtIni))
				return;
		}
		GcMovimentacao m = movimentar(informacao,
				GcTipoMovimentacao.TIPO_MOVIMENTACAO_VISITA, null, null, null,
				null, null, null, null, null, null);
		gravar(informacao, idc, titular, lotaTitular);
	}

	public void notificado(GcInformacao informacao, CpIdentidade idc,
			DpPessoa titular, DpLotacao lotaTitular,
			GcMovimentacao movNotificacao) throws Exception {
		for (GcMovimentacao movs : informacao.movs) {
			if (movs.isCancelada())
				continue;
			if (movs.tipo.id == movNotificacao.tipo.id) {
				if (titular.equivale(movNotificacao.pessoaAtendente)) {
					GcMovimentacao m = movimentar(informacao,
							GcTipoMovimentacao.TIPO_MOVIMENTACAO_CIENTE, null,
							null, null, null, null, null, movNotificacao, null,
							null);
					movNotificacao.movCanceladora = m;
					gravar(informacao, idc, titular, lotaTitular);
				} else if (lotaTitular
						.equivale(movNotificacao.lotacaoAtendente)) {
					GcMovimentacao m = movimentar(informacao,
							GcTipoMovimentacao.TIPO_MOVIMENTACAO_CIENTE, null,
							movNotificacao.lotacaoAtendente, null, null, null,
							null, movNotificacao, null, null);
					gravar(informacao, idc, titular, lotaTitular);
					if (m.todaLotacaoCiente(movNotificacao)) {
						movNotificacao.movCanceladora = m;
						gravar(informacao, idc, titular, lotaTitular);
					}
				} else {
					// Edson: desenvolver esquema para marcar ci�ncia de grupo
					// de e-mail,
					// chamando, inclusive, um todoGrupoCiente()
				}
				return;
			}
		}
	}

	public void interessado(GcInformacao informacao, CpIdentidade idc,
			DpPessoa titular, DpLotacao lotaTitular, boolean fInteresse)
			throws Exception {
		GcMovimentacao movLocalizada = null;
		for (GcMovimentacao mov : informacao.movs) {
			if (mov.isCancelada())
				continue;
			if (mov.tipo.id == GcTipoMovimentacao.TIPO_MOVIMENTACAO_INTERESSADO
					&& titular.equivale(mov.pessoaTitular)) {
				movLocalizada = mov;
				break;
			}
		}
		if (movLocalizada == null && fInteresse) {
			GcMovimentacao m = movimentar(informacao,
					GcTipoMovimentacao.TIPO_MOVIMENTACAO_INTERESSADO, null,
					null, null, null, null, null, null, null, null);
			gravar(informacao, idc, titular, lotaTitular);
		} else if (movLocalizada != null && !fInteresse) {
			cancelarMovimentacao(informacao, movLocalizada, idc,
					movLocalizada.pessoaTitular, movLocalizada.lotacaoTitular);
			/*
			 * GcMovimentacao m = GcBL .movimentar( informacao,
			 * GcTipoMovimentacao
			 * .TIPO_MOVIMENTACAO_CANCELAMENTO_DE_MOVIMENTACAO, null, null,
			 * null, null, null, movLocalizada, null, null, null);
			 * movLocalizada.movCanceladora = m; gravar(informacao, idc,
			 * movLocalizada.pessoaTitular, movLocalizada.lotacaoTitular);
			 */
		}
	}

	public void cancelar(GcInformacao informacao, CpIdentidade idc,
			DpPessoa titular, DpLotacao lotaTitular) throws Exception {
		Date dt = dt();
		for (GcMovimentacao mov : informacao.movs) {
			if (mov.isCancelada())
				continue;
			if (mov.tipo.id == GcTipoMovimentacao.TIPO_MOVIMENTACAO_CANCELAMENTO)
				return;
		}
		GcMovimentacao m = movimentar(informacao,
				GcTipoMovimentacao.TIPO_MOVIMENTACAO_CANCELAMENTO, null, null,
				null, null, null, null, null, null, null);
		gravar(informacao, idc, titular, lotaTitular);
	}

	public static int compareStrings(String s1, String s2) {
		if (s1 == null) {
			if (s2 != null)
				return -1;
			else
				return 0;
		}
		return s1.compareTo(s2);
	}

	private final String NON_THIN = "[^iIl1\\.,']";

	private int textWidth(String str) {
		return (int) (str.length() - str.replaceAll(NON_THIN, "").length() / 2);
	}

	public String ellipsize(String text, int max) {

		if (textWidth(text) <= max)
			return text;

		// Start by chopping off at the word before max
		// This is an over-approximation due to thin-characters...
		int end = text.lastIndexOf(' ', max - 3);

		// Just one long word. Chop it off.
		if (end == -1)
			return text.substring(0, max - 3) + "...";

		// Step forward as long as textWidth allows.
		int newEnd = end;
		do {
			end = newEnd;
			newEnd = text.indexOf(' ', end + 1);

			// No more spaces.
			if (newEnd == -1)
				newEnd = text.length();

		} while (textWidth(text.substring(0, newEnd) + "...") < max);

		return text.substring(0, end) + "...";
	}

	public String atualizarClassificacao(String classificacao, String hashTag) {
		if (classificacao.isEmpty() && hashTag.isEmpty())
			return null;
		else if (classificacao.isEmpty() && !hashTag.isEmpty())
			return hashTag.trim();
		else if (!classificacao.isEmpty() && hashTag.isEmpty())
			return classificacao;
		else
			return classificacao.concat(", ").concat(hashTag).trim();
	}

	public void cancelarMovimentacao(GcInformacao info, GcMovimentacao mov,
			CpIdentidade idc, DpPessoa titular, DpLotacao lotaTitular)
			throws Exception {
		GcMovimentacao m = movimentar(
				info,
				GcTipoMovimentacao.TIPO_MOVIMENTACAO_CANCELAMENTO_DE_MOVIMENTACAO,
				null, null, null, null, null, null, mov, null, null);
	//	gravar(info, idc, titular, lotaTitular);
		mov.movCanceladora = m;
		gravar(info, idc, titular, lotaTitular);
	}

	private String acronimoOrgao = null;
	private final int CONTROLE_LINK_HASH_TAG = 2;
	private final String URL_SIGA_DOC = "/sigaex/expediente/doc/exibir.action?sigla=";
	private final String URL_SIGA_SR = "/sigasr/solicitacao/exibir?sigla=";
	private final String URL_SIGA_GC = "/sigagc/app/exibir?sigla=";

	// public void salvar(Historico o) throws Exception {
	// o.setHisDtIni(new Date());
	// o.setHisDtFim(null);
	// if (o.getId() == null) {
	// ((GenericModel) o).save();
	// o.setHisIdIni(o.getId());
	// } else {
	// JPA.em().detach(o);
	// // Edson: Abaixo, nÃ£o funcionou findById, por algum motivo
	// // relacionado a esse esquema de sobrescrever o ObjetoBase
	// Historico oAntigo = (Historico) JPA.em().find(o.getClass(),
	// o.getId());
	// ((ManipuladorHistorico) oAntigo).finalizar();
	// o.setHisIdIni(oAntigo.getHisIdIni());
	// o.setId(null);
	// }
	// ((GenericModel) o).save();
	// }

	public void finalizar(Historico o) throws Exception {
		o.setHisDtFim(new Date());
		((Objeto) o).save();
	}

	// public GcConfiguracao getConfiguracao(DpPessoa pess,
	// SrItemConfiguracao item, SrServico servico, long idTipo,
	// SrSubTipoConfiguracao subTipo) throws Exception {
	//
	// GcConfiguracao conf = new GcConfiguracao(pess, item, servico, JPA.em()
	// .find(CpTipoConfiguracao.class, idTipo), subTipo);
	//
	// return GcConfiguracaoBL.get().buscarConfiguracao(conf);
	// }
	//
	// public List<GcConfiguracao> getConfiguracoes(DpPessoa pess,
	// SrItemConfiguracao item, SrServico servico, long idTipo,
	// SrSubTipoConfiguracao subTipo) throws Exception {
	// return getConfiguracoes(pess, item, servico, idTipo, subTipo,
	// new int[] {});
	// }
	//
	// public List<GcConfiguracao> getConfiguracoes(DpPessoa pess,
	// SrItemConfiguracao item, SrServico servico, long idTipo,
	// SrSubTipoConfiguracao subTipo, int atributoDesconsideradoFiltro[])
	// throws Exception {
	// GcConfiguracao conf = new GcConfiguracao(pess, item, servico, JPA.em()
	// .find(CpTipoConfiguracao.class, idTipo), subTipo);
	// return GcConfiguracaoBL.get().listarConfiguracoesAtivasPorFiltro(conf,
	// atributoDesconsideradoFiltro);
	// }

	public void copiar(Object dest, Object orig) {
		for (Method getter : orig.getClass().getDeclaredMethods()) {
			try {
				String getterName = getter.getName();
				if (!getterName.startsWith("get"))
					continue;
				if (Collection.class.isAssignableFrom(getter.getReturnType()))
					continue;
				String setterName = getterName.replace("get", "set");
				Object origValue = getter.invoke(orig);
				dest.getClass().getMethod(setterName, getter.getReturnType())
						.invoke(dest, origValue);
			} catch (NoSuchMethodException nSME) {
				int a = 0;
			} catch (IllegalAccessException iae) {
				int a = 0;
			} catch (IllegalArgumentException iae) {
				int a = 0;
			} catch (InvocationTargetException nfe) {
				int a = 0;
			}

		}
	}

	// Este mÃ©todo Ã© usado por classes para as quais o mapeamento de
	// sequence
	// nÃ£o estÃ¡ funcionando. Isso estÃ¡ ocorrendo porque, quando a
	// opÃ§Ã£o
	// jpa.ddl
	// estÃ¡ setada como validate (em vez de create-drop, por exemplo), o
	// Hibernate tenta conferir erroneamente (JIRA HHH-2508) se uma sequence
	// mapeada estÃ¡ entre as user_sequences, ou seja, entre as sequences do
	// banco cujo owner Ã© sigasr. Como hÃ¡ sequences do usuÃ¡rio
	// Corporativo,
	// nÃ£o
	// do sigasr, a aplicaÃ§Ã£o sigasr nÃ£o inicia por erro de
	// validaÃ§Ã£o do
	// Hibernate. Comentei os mapeamentos de Ã­ndice por anotaÃ§Ã£o (que
	// Ã© o
	// modo
	// de mapear usado pelo sigasr) no Corporativo, pra nÃ£o dar erro de
	// validaÃ§Ã£o. Ver soluÃ§Ã£o melhor o mais
	// rÃ¡pido possÃ­vel. Ainda, como o sigasr precisa usar sequences do
	// Corporativo (em SrMarca e GcConfiguracao) e as anotaÃ§Ãµes nÃ£o
	// estÃ£o
	// presentes, este mÃ©todo Ã© necessÃ¡rio.
	public Long nextVal(String sequence) {
		Long newId;
		return Long.valueOf(em()
				.createNativeQuery("select " + sequence + ".nextval from dual")
				.getSingleResult().toString());
	}

	private EntityManager em() {
		return this.em;
	}

	/**
	 * Cria um link referenciando automaticamente um
	 * documento/serviço/conhecimento quando é acrescentado o seu código no
	 * campo de conteúdo da informação. Ex: Estou editando um conhecimento,
	 * no seu campo texto quero referenciar o seguinte documento
	 * JFRJ-OFI-2013/00003. Quando acrescento esse código do ofício e mando
	 * salvar as alterações do conhecimento é criado um link que leva direto
	 * ao documento referenciado.
	 * 
	 * Além disso, também identifica e cria links para hashTags. Esses
	 * hashTags são inseridos no campo de classificação do conhecimento.
	 * 
	 **/
	public String marcarLinkNoConteudo(String conteudo) throws Exception {

		if (acronimoOrgao == null) {
			acronimoOrgao = "";
			List<String> acronimo = CpOrgaoUsuario.AR.find(
					"select acronimoOrgaoUsu from CpOrgaoUsuario").fetch();
			for (String ao : acronimo)
				acronimoOrgao += (acronimoOrgao.isEmpty() ? "" : "|") + ao;
		}
		conteudo = findSigla(conteudo);
		return findHashTag(conteudo, null, CONTROLE_LINK_HASH_TAG);
	}

	private String findSigla(String conteudo) throws Exception {
		String sigla = null;
		GcInformacao infoReferenciada = null;
		StringBuffer sb = new StringBuffer();

		// lembrar de retirar o RJ quando for para a produção.
		Pattern padraoSigla = Pattern.compile(
		// reconhece tais tipos de códigos: JFRJ-EOF-2013/01494.01,
		// JFRJ-REQ-2013/03579-A, JFRJ-EOF-2013/01486.01-V01,
		// TRF2-PRO-2013/00001-V01
				"(?i)(?:(?:RJ|"
						+ acronimoOrgao
						+ ")-([A-Za-z]{2,3})-[0-9]{4}/[0-9]{5}(?:.[0-9]{2})?(?:-V[0-9]{2})?(?:-[A-Za-z]{1})?)");

		Matcher matcherSigla = padraoSigla.matcher(conteudo);
		while (matcherSigla.find()) {
			// identifica que é um código de um conhecimento, ou serviço ou
			// documento
			if (matcherSigla.group(1) != null) {
				sigla = matcherSigla.group(0).toUpperCase().trim();
				// conhecimento
				if (matcherSigla.group(1).toUpperCase().equals("GC")) {
					infoReferenciada = GcInformacao.findBySigla(sigla);
					matcherSigla.appendReplacement(sb, "[[" + URL_SIGA_GC
							+ URLEncoder.encode(sigla, "UTF-8") + "|" + sigla
							+ " - " + infoReferenciada.arq.titulo + "]]");
				}
				// serviço
				else if (matcherSigla.group(1).toUpperCase().equals("SR")) {
					matcherSigla.appendReplacement(sb, "[[" + URL_SIGA_SR
							+ URLEncoder.encode(sigla, "UTF-8") + "|" + sigla
							+ "]]");
				}
				// documento
				else {
					matcherSigla.appendReplacement(sb, "[[" + URL_SIGA_DOC
							+ URLEncoder.encode(sigla, "UTF-8") + "|" + sigla
							+ "]]");
				}
			}
		}
		matcherSigla.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Método que encontra uma hashTag. Quando o parâmetro controle é igual a
	 * 1, a classificação é atualizada para poder ser gravada. Quando o
	 * controle é igual a 2, o conteudo é marcado com os links das hashTags
	 * encontradas. O conteudo não é gravado com os links.
	 */
	public String findHashTag(String conteudo, String classificacao,
			int controle) {
		StringBuffer sb = new StringBuffer();
		String hashTag = new String();

		Pattern padraoHashTag = Pattern.compile(
		// reconhece uma hashTag (#)
				"(#[\\w-]+)");

		Matcher matcherHashTag = padraoHashTag.matcher(conteudo);
		while (matcherHashTag.find()) {
			if (controle == 1)
				hashTag += (hashTag.isEmpty() ? "" : ", ")
						+ matcherHashTag.group(0);
			else if (controle == 2) {
				matcherHashTag.appendReplacement(sb,
						"[[/sigagc/app/listar?filtro.pesquisa=true&filtro.tag.sigla="
								+ matcherHashTag.group(0).substring(1)
								+ "|$0]]");
			}
		}
		if (controle == 1) {
			if (classificacao != null)
				// remove todas as hashTag da classificacao, caso exista.
				// Necessário para manter a classificacao
				// atualizada. Ao final serão inseridas as hashTags que foram
				// acrescentadas/mantidas no conteudo
				classificacao = classificacao
						.replaceAll("[,\\s]*#[,\\w-]+", "").trim();
			else
				classificacao = "";
			return atualizarClassificacao(classificacao, hashTag);
		} else if (controle == 2) {
			matcherHashTag.appendTail(sb);
			return sb.toString();
		} else
			return null;
	}

	public String escapeHashTag(String conteudo) {
		StringBuffer sb = new StringBuffer();
		// String hashTag = new String();

		Pattern padraoHashTag = Pattern.compile(
		// reconhece uma hashTag (#)
				"(#[\\w-]+)");

		Matcher matcherHashTag = padraoHashTag.matcher(conteudo);
		while (matcherHashTag.find()) {
			matcherHashTag.appendReplacement(sb,
					"{{{" + matcherHashTag.group(0) + "}}}");
		}
		matcherHashTag.appendTail(sb);
		return sb.toString();
	}

	public String findHashTagHTML(String conteudo, String classificacao,
			int controle) {
		StringBuffer sb = new StringBuffer();
		String hashTag = new String();

		Pattern padraoHashTag = Pattern.compile(
		// reconhece uma hashTag (#)
				"(#[\\w-]+)");

		Matcher matcherHashTag = padraoHashTag.matcher(conteudo);
		while (matcherHashTag.find()) {
			if (controle == 1)
				hashTag += (hashTag.isEmpty() ? "" : ", ")
						+ matcherHashTag.group(0);
			else if (controle == 2) {
				matcherHashTag.appendReplacement(sb,
						"<a href=\"/sigagc/app/listar?filtro.pesquisa=true&filtro.tag.sigla="
								+ matcherHashTag.group(0).substring(1)
								+ "\">$0</a>");
			}
		}
		if (controle == 1) {
			if (classificacao != null)
				// remove todas as hashTag da classificacao, caso exista.
				// Necessário para manter a classificacao
				// atualizada. Ao final serão inseridas as hashTags que foram
				// acrescentadas/mantidas no conteudo
				classificacao = classificacao
						.replaceAll("[,\\s]*#[,\\w-]+", "").trim();
			else
				classificacao = "";
			return atualizarClassificacao(classificacao, hashTag);
		} else if (controle == 2) {
			matcherHashTag.appendTail(sb);
			return sb.toString();
		} else
			return null;
	}

}