import java.util.PriorityQueue;

/**
 * Simulador de Rede de Filas em Tandem 
 */
public class SimuladorFilasTandem {

    // ─── Gerador LCG ────────────────────────────────────────────────────────────
    static final long M = 2147483647L; // 2^31 - 1
    static final long A = 1103515245L;
    static final long C = 12345L;
    static final long SEMENTE_INICIAL = 42L;
    static final int LIMITE_ALEATORIOS = 100_000;

    static long sementeAtual = SEMENTE_INICIAL;
    static int aleatoriosUsados = 0;
    static boolean limiteAtingido = false;

    static void resetGerador() {
        sementeAtual = SEMENTE_INICIAL;
        aleatoriosUsados = 0;
        limiteAtingido = false;
    }

    /** Retorna próximo número em (0,1) ou null se limite atingido. */
    static Double nextRandom() {
        if (aleatoriosUsados >= LIMITE_ALEATORIOS)
            return null;
        sementeAtual = (A * sementeAtual + C) % M;
        aleatoriosUsados++;
        if (aleatoriosUsados == LIMITE_ALEATORIOS)
            limiteAtingido = true;
        return (double) sementeAtual / M;
    }

    /** Sorteia tempo uniforme [min, max]. Retorna null se limite atingido. */
    static Double sortearTempo(double min, double max) {
        Double u = nextRandom();
        if (u == null)
            return null;
        return min + (max - min) * u;
    }

    // ─── Classe Evento ───────────────────────────────────────────────────────────
    enum TipoEvento {
        CHEGADA, SAIDA, PASSAGEM
    }

    static class Evento implements Comparable<Evento> {
        TipoEvento tipo;
        double tempo;
        int filaOrigem; // fila que gerou o evento (índice)
        int filaDestino; // usado apenas em PASSAGEM

        Evento(TipoEvento tipo, double tempo, int filaOrigem, int filaDestino) {
            this.tipo = tipo;
            this.tempo = tempo;
            this.filaOrigem = filaOrigem;
            this.filaDestino = filaDestino;
        }

        @Override
        public int compareTo(Evento outro) {
            // empate: saída/passagem antes de chegada
            int cmp = Double.compare(this.tempo, outro.tempo);
            if (cmp != 0)
                return cmp;
            // SAIDA e PASSAGEM têm prioridade sobre CHEGADA
            int prioThis = (this.tipo == TipoEvento.CHEGADA) ? 1 : 0;
            int prioOutro = (outro.tipo == TipoEvento.CHEGADA) ? 1 : 0;
            return Integer.compare(prioThis, prioOutro);
        }
    }

    // ─── Classe Fila ─────────────────────────────────────────────────────────────
    static class Fila {
        String nome;
        int servidores;
        int capacidade;
        double minAtendimento;
        double maxAtendimento;
        double minChegada; // usado apenas na fila com chegadas externas
        double maxChegada;

        int customers; // clientes no sistema (fila + servidores)
        int servidoresOcupados;
        int perdas;

        double[] temposEstados; // temposEstados[n] = tempo acumulado com n clientes
        double[] saidasServidores; // tempo de saída agendado para cada servidor

        Fila(String nome, int servidores, int capacidade,
                double minAtendimento, double maxAtendimento,
                double minChegada, double maxChegada) {
            this.nome = nome;
            this.servidores = servidores;
            this.capacidade = capacidade;
            this.minAtendimento = minAtendimento;
            this.maxAtendimento = maxAtendimento;
            this.minChegada = minChegada;
            this.maxChegada = maxChegada;

            this.customers = 0;
            this.servidoresOcupados = 0;
            this.perdas = 0;
            this.temposEstados = new double[capacidade + 1];
            this.saidasServidores = new double[servidores];
            for (int i = 0; i < servidores; i++)
                saidasServidores[i] = Double.POSITIVE_INFINITY;
        }

        int status() {
            return customers;
        }

        int capacity() {
            return capacidade;
        }

        int servers() {
            return servidores;
        }

        void in() {
            customers++;
        }

        void out() {
            customers--;
        }

        void loss() {
            perdas++;
        }

        /** Índice de servidor livre; -1 se nenhum. */
        int servidorLivre() {
            for (int i = 0; i < servidores; i++)
                if (Double.isInfinite(saidasServidores[i]))
                    return i;
            return -1;
        }

        /** Índice do servidor com menor saída agendada. */
        int proximoServidor() {
            int idx = -1;
            double menor = Double.POSITIVE_INFINITY;
            for (int i = 0; i < servidores; i++) {
                if (saidasServidores[i] < menor) {
                    menor = saidasServidores[i];
                    idx = i;
                }
            }
            return idx;
        }
    }

    // ─── Simulação principal ─────────────────────────────────────────────────────

    static void simular(Fila[] filas, double[][] routing,
            int primeiraChegadaFila, double tempoPrimeiraChegada) {
        resetGerador();

        double tempoAtual = 0.0;
        PriorityQueue<Evento> escalonador = new PriorityQueue<>();

        // Agenda primeira chegada externa
        escalonador.add(new Evento(TipoEvento.CHEGADA, tempoPrimeiraChegada,
                primeiraChegadaFila, -1));

        while (!escalonador.isEmpty()) {
            Evento ev = escalonador.poll();

            // Acumula tempo em todas as filas
            double delta = ev.tempo - tempoAtual;
            for (Fila f : filas) {
                int estado = Math.min(f.status(), f.capacity());
                f.temposEstados[estado] += delta;
            }
            tempoAtual = ev.tempo;

            switch (ev.tipo) {
                case CHEGADA -> processarChegada(ev, filas, routing, escalonador, tempoAtual);
                case SAIDA -> processarSaida(ev, filas, routing, escalonador, tempoAtual);
                case PASSAGEM -> processarPassagem(ev, filas, routing, escalonador, tempoAtual);
            }

            if (limiteAtingido)
                break;
        }

        // Imprime resultados
        System.out.printf("%nTempo global da simulação: %.6f%n", tempoAtual);
        System.out.printf("Aleatórios usados: %d%n%n", aleatoriosUsados);

        for (Fila f : filas) {
            System.out.printf("=== %s (G/G/%d/%d) ===%n",
                    f.nome, f.servidores, f.capacidade);
            System.out.printf("  Perdas: %d%n", f.perdas);
            System.out.println("  Tempos acumulados por estado:");
            for (int n = 0; n <= f.capacidade; n++) {
                double prob = (tempoAtual > 0) ? f.temposEstados[n] / tempoAtual : 0;
                System.out.printf("    Estado %2d: tempo = %12.6f  prob = %.8f%n",
                        n, f.temposEstados[n], prob);
            }
            System.out.println();
        }
    }

    // ── Chegada externa ──────────────────────────────────────────────────────────
    static void processarChegada(Evento ev, Fila[] filas, double[][] routing,
            PriorityQueue<Evento> esc, double tempoAtual) {
        Fila f = filas[ev.filaOrigem];

        if (f.status() < f.capacity()) {
            f.in();
            // Se há servidor livre, inicia atendimento
            int srv = f.servidorLivre();
            if (srv != -1) {
                Double ta = sortearTempo(f.minAtendimento, f.maxAtendimento);
                if (ta == null)
                    return;
                f.saidasServidores[srv] = tempoAtual + ta;
                // Agenda evento de saída ou passagem
                agendarSaidaOuPassagem(ev.filaOrigem, srv, filas, routing, esc, tempoAtual + ta);
            }
        } else {
            f.loss();
        }

        // Agenda próxima chegada externa (mesma fila)
        Double tec = sortearTempo(f.minChegada, f.maxChegada);
        if (tec == null)
            return;
        esc.add(new Evento(TipoEvento.CHEGADA, tempoAtual + tec, ev.filaOrigem, -1));
    }

    // ── Saída do sistema ─────────────────────────────────────────────────────────
    static void processarSaida(Evento ev, Fila[] filas, double[][] routing,
            PriorityQueue<Evento> esc, double tempoAtual) {
        Fila f = filas[ev.filaOrigem];
        int srv = ev.filaDestino; // aqui filaDestino carrega o índice do servidor

        f.saidasServidores[srv] = Double.POSITIVE_INFINITY;
        f.out();

        // Se há clientes esperando, atende próximo
        if (f.status() >= f.servers()) { // ainda há clientes na fila de espera
            Double ta = sortearTempo(f.minAtendimento, f.maxAtendimento);
            if (ta == null)
                return;
            f.saidasServidores[srv] = tempoAtual + ta;
            agendarSaidaOuPassagem(ev.filaOrigem, srv, filas, routing, esc, tempoAtual + ta);
        }
    }

    // ── Passagem entre filas ─────────────────────────────────────────────────────
    static void processarPassagem(Evento ev, Fila[] filas, double[][] routing,
            PriorityQueue<Evento> esc, double tempoAtual) {
        // Libera servidor na fila de origem
        Fila origem = filas[ev.filaOrigem];
        int srv = ev.filaDestino >> 16; // índice do servidor codificado na parte alta
        int destIdx = ev.filaDestino & 0xFFFF; // índice da fila destino na parte baixa

        origem.saidasServidores[srv] = Double.POSITIVE_INFINITY;
        origem.out();

        // Se há clientes esperando na fila de origem, atende próximo
        if (origem.status() >= origem.servers()) {
            Double ta = sortearTempo(origem.minAtendimento, origem.maxAtendimento);
            if (ta == null)
                return;
            origem.saidasServidores[srv] = tempoAtual + ta;
            agendarSaidaOuPassagem(ev.filaOrigem, srv, filas, routing, esc, tempoAtual + ta);
        }

        // Cliente entra na fila destino
        Fila dest = filas[destIdx];
        if (dest.status() < dest.capacity()) {
            dest.in();
            int srvDest = dest.servidorLivre();
            if (srvDest != -1) {
                Double ta = sortearTempo(dest.minAtendimento, dest.maxAtendimento);
                if (ta == null)
                    return;
                dest.saidasServidores[srvDest] = tempoAtual + ta;
                agendarSaidaOuPassagem(destIdx, srvDest, filas, routing, esc, tempoAtual + ta);
            }
        } else {
            dest.loss();
        }
    }

    static void agendarSaidaOuPassagem(int filaIdx, int srv, Fila[] filas,
            double[][] routing, PriorityQueue<Evento> esc,
            double tempo) {
        double[] probs = routing[filaIdx];
        Double u = nextRandom();
        if (u == null)
            return;

        double acum = 0.0;
        for (int j = 0; j < probs.length; j++) {
            acum += probs[j];
            if (u < acum) {
                // Vai para fila j — codifica srv e destino juntos
                esc.add(new Evento(TipoEvento.PASSAGEM, tempo, filaIdx, (srv << 16) | j));
                return;
            }
        }
        // Sai do sistema
        esc.add(new Evento(TipoEvento.SAIDA, tempo, filaIdx, srv));
    }

    // ─── main ────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        Fila[] filas = new Fila[] {
                // nome, servidores, capacidade, minAtend, maxAtend, minCheg, maxCheg
                new Fila("Fila 1", 2, 3, 3.0, 4.0, 1.0, 4.0),
                new Fila("Fila 2", 1, 5, 2.0, 3.0, 0.0, 0.0) // sem chegadas externas
        };

        // Matriz de roteamento: routing[i][j] = prob. de ir da fila i para fila j
        double[][] routing = new double[][] {
                { 0.0, 1.0 }, // Fila 1: 100% vai para Fila 2
                { 0.0, 0.0 }, // Fila 2: 100% sai do sistema
        };

        int filaComChegadasExternas = 0; // índice da fila que recebe clientes de fora
        double tempoPrimeiraChegada = 1.5;

        simular(filas, routing, filaComChegadasExternas, tempoPrimeiraChegada);
    }
}