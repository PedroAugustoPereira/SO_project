// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Estrutura deste código:
//    Todo código está dentro da classe *Sistema*
//    Dentro de Sistema, encontra-se acima a definição de HW:
//           Memory,  Word, 
//           CPU tem Opcodes (codigos de operacoes suportadas na cpu),
//               e Interrupcoes possíveis, define o que executa para cada instrucao
//           VM -  a máquina virtual é uma instanciação de CPU e Memória
//    Depois as definições de SW:
//           no momento são esqueletos (so estrutura) para
//					InterruptHandling    e
//					SysCallHandling 
//    A seguir temos utilitários para usar o sistema
//           carga, início de execução e dump de memória
//    Por último os programas existentes, que podem ser copiados em memória.
//           Isto representa programas armazenados.
//    Veja o main.  Ele instancia o Sistema com os elementos mencionados acima.
//           em seguida solicita a execução de algum programa com  loadAndExec
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Semaphore;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public Word[] pos; // pos[i] é a posição i da memória. cada posição é uma palavra.
		public int tamMem;

		public Memory(int size) {
			this.tamMem = size;
			pos = new Word[size];
			for (int i = 0; i < pos.length; i++) {
				pos[i] = new Word(Opcode.___, -1, -1, -1);
			}
			; // cada posicao da memoria inicializada
		}
	}

	public class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int ra; // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int rb; // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _ra, int _rb, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			ra = _ra;
			rb = _rb;
			p = _p;
		}

		public String toString(){
			return "opc: " + this.opc + " ra: " + this.ra + " rb: " + this.rb + " p: " + this.p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios
		JMPIM, JMPIGM, JMPILM, JMPIEM,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT, // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		SYSCALL, STOP // chamada de sistema e parada
	}

	public enum Interrupts { // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP, intIORequestIsReady, intIORequestVMIsReady;
	}

	public class CPU implements Runnable{
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;

		// CONTEXTO da CPU ...
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		// FIM CONTEXTO DA CPU: tudo que precisa sobre o estado de um processo para
		// executa-lo
		// nas proximas versoes isto pode modificar

		private Word[] m; // m é o array de memória "física", CPU tem uma ref a m para acessar
		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema
		private boolean cpuStop; // flag para parar CPU - caso de interrupcao e stop - nesta versao acaba o sistema no fim do prog

		// auxilio aa depuração
		private boolean debug; // se true entao mostra cada instrucao em execucao
		private Utilities u; // para debug (dump)

		private Semaphore readyCPU, finishedCPU;

		public CPU(Memory _mem, boolean _debug, Semaphore _readyCPU, Semaphore _finishedCPU) { // ref a MEMORIA e interrupt handler passada na criacao da CPU
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			m = _mem.pos; // usa o atributo 'm' para acessar a memoria, só para ficar mais pratico
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			readyCPU = _readyCPU;
			finishedCPU = _finishedCPU;

			debug = _debug; // se true, print da instrucao em execucao
		}

		public void setUtilities(Utilities _u) {
			u = _u;
		}

		public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
		}

		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
			if (e >= 0 && e < m.length) {
				boolean addrValido = e < so.processManager.running.pages.size() * so.tamPage;
				if (!addrValido) {
					System.out.println("Endereço lógico inválido: " + e + "\nProccesso: " + so.processManager.running.name + "\n id: " +so.processManager.running.id);
					irpt = Interrupts.intEnderecoInvalido;
				}
				return addrValido;
			} else {
				irpt = Interrupts.intEnderecoInvalido;
				return false;
			}
		}

		private boolean testOverflow(int v) { // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}

		public void setContext(int _pc) {
			pc = _pc; // pc cfe endereco logico
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run(boolean useScheduler) { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente
			boolean checkPageFault = false;				// setado					
			cpuStop = false;
			while (!cpuStop) { // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// --------------------------------------------------------------------------------------------------
				// FETCH
				checkPageFault = so.vmManager.checkPageFault(so.processManager.running.pages, so.processManager.running.pc);
				if (so.processManager.running != null && !checkPageFault) {
					pc = so.processManager.running.pc;
					if (legal(pc)) { // pc valido
						pc = so.processManager.translateAddress(so.processManager.running.pc);

						ir = m[pc]; // <<<<<<<<<<<< busca posicao da memoria apontada por pc, guarda em ir
						if (debug) {
							System.out.print("                                                  regs: ");
							for (int i = 0; i < 8; i++) {
								System.out.print("    r[" + i + "]:" + reg[i]);
							}
							;
							System.out.println();
						}
						if (debug) {
							System.out.print("                               pc: " + pc + "       exec: ");
							u.dump(ir);
						}
						
						// --------------------------------------------------------------------------------------------------
						// EXECUTA INSTRUCAO NO ir
						switch (ir.opc) { // conforme o opcode (código de operação) executa

							// Instrucoes de Busca e Armazenamento em Memoria
							case LDI: // Rd ← k
								reg[ir.ra] = ir.p;
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							case LDD: // Rd <- [A]
								if (legal(ir.p)) {
									reg[ir.ra] = m[ir.p].p;
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case LDX: // RD <- [RS] // NOVA
								if (legal(reg[ir.rb])) {
									reg[ir.ra] = m[reg[ir.rb]].p;
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case STD: // [A] ← Rs
								if (legal(ir.p)) {
									int address = so.processManager.translateAddress(ir.p);
									if(address != -1) {
										m[address].opc = Opcode.DATA;
										m[address].p = reg[ir.ra];
									}
									
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								;
								break;
							case STX: // [Rd] ←Rs
								if (legal(reg[ir.ra])) {
									int address = so.processManager.translateAddress(reg[ir.ra]);
									m[address].opc = Opcode.DATA;
									m[address].p = reg[ir.rb];
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								;
								break;
							case MOVE: // RD <- RS
								reg[ir.ra] = reg[ir.rb];
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							// Instrucoes Aritmeticas
							case ADD: // Rd ← Rd + Rs
								reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
								testOverflow(reg[ir.ra]);
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							case ADDI: // Rd ← Rd + k
								reg[ir.ra] = reg[ir.ra] + ir.p;
								testOverflow(reg[ir.ra]);
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							case SUB: // Rd ← Rd - Rs
								reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
								testOverflow(reg[ir.ra]);
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							case SUBI: // RD <- RD - k // NOVA
								reg[ir.ra] = reg[ir.ra] - ir.p;
								testOverflow(reg[ir.ra]);
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;
							case MULT: // Rd <- Rd * Rs
								reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
								testOverflow(reg[ir.ra]);
								so.processManager.running.incLogicProgramCounter();
								pc++;
								break;

							// Instrucoes JUMP
							case JMP: // PC <- k
								so.processManager.running.setPc(ir.p);
								pc = ir.p;
								break;
							case JMPIM: // PC <- [A]
								so.processManager.running.setPc(m[ir.p].p);
								pc = m[ir.p].p;
								break;
							case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
								if (reg[ir.rb] > 0) {
									so.processManager.running.setPc(reg[ir.ra]);
									pc = reg[ir.ra];
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIGK: // If RC > 0 then PC <- k else PC++
								if (reg[ir.rb] > 0) {
									so.processManager.running.setPc(ir.p);
									pc = ir.p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPILK: // If RC < 0 then PC <- k else PC++
								if (reg[ir.rb] < 0) {
									so.processManager.running.setPc(ir.p);
									pc = ir.p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIEK: // If RC = 0 then PC <- k else PC++
								if (reg[ir.rb] == 0) {
									so.processManager.running.setPc(ir.p);
									pc = ir.p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
								if (reg[ir.rb] < 0) {
									so.processManager.running.setPc(reg[ir.ra]);
									pc = reg[ir.ra];
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
								if (reg[ir.rb] == 0) {
									so.processManager.running.setPc(reg[ir.ra]);
									pc = reg[ir.ra];
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIGM: // If RC > 0 then PC <- [A] else PC++
								if (reg[ir.rb] > 0) {
									so.processManager.running.setPc(m[ir.p].p);
									pc = m[ir.p].p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPILM: // If RC < 0 then PC <- k else PC++
								if (reg[ir.rb] < 0) {
									so.processManager.running.setPc(m[ir.p].p);
									pc = m[ir.p].p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIEM: // If RC = 0 then PC <- k else PC++
								if (reg[ir.rb] == 0) {
									so.processManager.running.setPc(m[ir.p].p);
									pc = m[ir.p].p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;
							case JMPIGT: // If RS>RC then PC <- k else PC++
								if (reg[ir.ra] > reg[ir.rb]) {
									so.processManager.running.setPc(ir.p);
									pc = ir.p;
								} else {
									so.processManager.running.incLogicProgramCounter();
									pc++;
								}
								break;

							case DATA: // pc está sobre área supostamente de dados
								irpt = Interrupts.intInstrucaoInvalida;
								break;

							// Chamadas de sistema
							case SYSCALL:
								so.processManager.running.incLogicProgramCounter();
								sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
								
								cpuStop = true;// temos IO
								pc++;
								break;

							case STOP: // por enquanto, para execucao
								sysCall.stop();
								System.out.println("1 processo finalizado: \nNome:" +so.processManager.running.name + "\n" + "ID: " +so.processManager.running.id + "\n");
								System.out.println("Final Dump: ");
								u.dump(so.processManager.running.id);
								so.processManager.dieProcess(so.processManager.running.id);
								so.processManager.running = null;

								if(so.processManager.readyQueue.size() > 0 && useScheduler)
									so.scheduler.runningNext(null);
								else
									cpuStop = true;
								break;

							// Inexistente
							default:
								irpt = Interrupts.intInstrucaoInvalida;
								break;
						}
					}
				} else{
					if(checkPageFault){
						so.vmManager.generatePageFault();	
					}
					cpuStop = true;	
				}
				
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (irpt != Interrupts.noInterrupt) { // existe interrupção
					ih.handle(irpt); // desvia para rotina de tratamento
					cpuStop = true;
				}

				if(so.scheduler.checkTimerInterrupt() ){
					cpuStop = true;
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}

		@Override
		public void run() {
			while (true) {
				try {
					// CPU aguarda sinal para rodar (semáforo)
					readyCPU.acquire();
					
					// Processo está rodando
					//System.out.println("CPU is running");
					// Roda o processo na CPU (possui o timer de interrupção lá dentro)
					hw.cpu.run(false);
					// Processo finalizou ou foi interrompido, CPU livre
					//	System.out.println("CPU is free");
	
					// Libera o semáforo para o escalonador (semáforo)
					finishedCPU.release();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// Espera o sinal para liberar a CPU rodar (semáforo)
			}
		}
	}
	// ------------------ C P U - fim
	// -----------------------------------------------------------------------
	// ------------------------------------------------------------------------------------------------------

	// ------------------- HW - constituido de CPU e MEMORIA
	// -----------------------------------------------
	public class HW {
		public Memory mem;
		public CPU cpu;

		public HW(int tamMem, Semaphore readyCPU, Semaphore finishedCPU) {
			mem = new Memory(tamMem);
			cpu = new CPU(mem, true, readyCPU, finishedCPU); // true liga debug
		}
	}
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- SW - inicio - Sistema Operacional
	// -------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		private HW hw; // referencia ao hw se tiver que setar algo

		public InterruptHandling(HW _hw) {
			hw = _hw;
		}

		public void handle(Interrupts irpt) {
			// apenas avisa - todas interrupcoes neste momento finalizam o programa
			if(irpt == Interrupts.intEnderecoInvalido){
				invalidAddress();	
			}
			else if(irpt == Interrupts.intIORequestIsReady){
				//Trata a finalização da requisição
				so.ioManager.onRequestIsReady();
			}
			else if(irpt == Interrupts.intIORequestVMIsReady){
				so.vmManager.onRequestIsReady();
			}
				

			
		}

		public void invalidAddress(){
			System.out.println(" Interrupcao por endereço inválido, pc: " + hw.cpu.pc);
			so.processManager.dieProcess(so.processManager.running.id);
			so.processManager.running = null;		
		}
	}

	//Classe aninhada para requsições de IO
	public class IOManager {
		//Classe da requisição de IO
		public class RequestIO {
			public String pid;	//ID de processo
			public String type; //Tipo de requisição, IN ou OUT
			public int address; //Endereço de memória para outpuy, ou salvar input

			public RequestIO(String pid, String type, int address) {
				this.pid = pid;
				this.type = type;
				this.address = address;
			}
		}

		//Fila de processos bloqueados por IO
		private Queue<ProcessManager.ProcessControlBlock> blockedQueue;
		//Fila de requisições de IO
		private volatile Queue<RequestIO> requestIOQueue;

		public IOManager() {
			blockedQueue = new LinkedList<>();
			requestIOQueue = new LinkedList<>();
		}

		/**
		 * Adiciona uma nova requisição de IO
		 * @param pcb processo que está fazendo a requisição
		 * @param type tipo de requisição, IN ou OUT
		 * @param address endereço de memória para output, ou salvar input
		 */
		public void addRequestIO(ProcessManager.ProcessControlBlock pcb, String type, int address) {
			requestIOQueue.add(new RequestIO(pcb.id, type, so.processManager.translateAddress(address)));
		}

		/**
		 * Adiciona um processo na fila de bloqueados por IO
		 * @param pcb processo a ser bloqueado
		 */
		public void addBlockedProcess(ProcessManager.ProcessControlBlock pcb) {
			so.processManager.running = null;
			blockedQueue.add(pcb);
		}

		/**
		 * Método chamado quando a requisição de IO está pronta
		 */
		public void onRequestIsReady(){
			try{
				so.readyQueueControl.acquire();
				//Pega o processo da fila de bloqueados 
				ProcessManager.ProcessControlBlock pcb = blockedQueue.poll();
				//Passa ele para pronto e finaliza a requisição
				so.processManager.readyQueue.add(pcb);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				so.readyQueueControl.release(); 
			}
			
			so.console.onEndRequest();
		}
	}

	public class Console implements Runnable{
		public IOManager.RequestIO currentRequest;       //Requisição atual a ser processada
		private boolean pendingRequest;                  //Indica se a requisição atual está pendente (esperando input)
		
		@Override
		public void run() {
			this.currentRequest  = null;
			this.pendingRequest = false;

			//Looping olhando para a fila de processos bloqueados por IO
			while(true){
				//Se temos processo para pegar, e não tivermos um atual
				if(currentRequest == null && so.ioManager.requestIOQueue.size() > 0 && !pendingRequest){
					System.out.println("Processando requisição de IO");
					currentRequest = so.ioManager.requestIOQueue.poll();
					processRequest();
				}

				//Processa a requisição atua	
			}
		}

		/**
		 * Processa a requisição, (e a espera) enquanto houver
		 */
		private void processRequest(){
			if(!this.pendingRequest && this.currentRequest != null){
				if (currentRequest.type.equals("IN")) {
					//Agora essa request está pendente, esperando o input
					this.pendingRequest = true;        

					//Se for input, devemos printar o nome do processo e que o mesmo está esperando um input
					System.out.println("Processo " + currentRequest.pid + " esperando comando de input");
					System.out.println("Template: in " +currentRequest.pid + " [Valor desejado] ");

					//Nesse momento esperamos a Thread UserShell receber um comando para prosseguir
				} else {
					int value = hw.mem.pos[currentRequest.address].p;
					outputCommand(value);
				}
			}
		}

		/**
		 * Recebe um comando de input vindo do terminal e o guarda em memória
		 * Cria a interrupção para ser tratada pela CPU
		 * @param value valor inteiro recebido pelo terminal
		 */
		public void inputCommand(int pId, int value){
			if (pendingRequest){
				hw.mem.pos[currentRequest.address].p = value;
				onCreateInterrupt();	
			}
		}

		/**
		 * Imprime o valor de output do processo
		 * Cria a interrupção para ser tratada pela cpu
		 * @param value valor de output
		 */
		public void outputCommand(int value){
			System.out.println("==========================================================");
			System.out.println("Processo " + currentRequest.pid + "\nOUTPUT:" + value);
			System.out.println("==========================================================");
			onCreateInterrupt();
		}

		/**
		 * Gera a uma interrupção para CPU do tipo IOReady
		 */
		public void onCreateInterrupt(){
			hw.cpu.irpt = Interrupts.intIORequestIsReady;
		}

		/**
		 * Limpa variaveis de controle da requisição atual para tratar a próxima
		 */
		public void onEndRequest(){
			this.currentRequest  = null;
			this.pendingRequest  = false;
		}
	}

	public class VirtualMemoryManager{
		public Queue<RequestIOVirtualMemory> requests;
		public Queue<ProcessManager.ProcessControlBlock> blockedQueueByVM; 

		public VirtualMemoryManager(){
			requests = new LinkedList<>();
			blockedQueueByVM = new LinkedList<>();
		}

		public void addRequestIOVM(ProcessManager.ProcessControlBlock pcb, String name, int page){
			requests.add(new RequestIOVirtualMemory(pcb.id, name, page));
		}

		public void addBlockedProcess(ProcessManager.ProcessControlBlock pcb){
			so.processManager.running = null;
			blockedQueueByVM.add(pcb);
		}

		public boolean checkPageFault(ArrayList<Integer> pages, int logicalAddress){
			int page = this.getPage(logicalAddress);	
			
			if(pages.get(page) == -1 || pages.get(page) == null){
				return true;
			}

			return false;
		}

		public int getPage(int logicalAddress){
			int page = (int) Math.floor(logicalAddress / so.tamPage);
			return page;		
		}

		public void onRequestIsReady(){
			ProcessManager.ProcessControlBlock pcb = blockedQueueByVM.poll();
			so.processManager.readyQueue.add(pcb);
			so.vmWorker.onEndRequest();
		}

		public void generatePageFault(){
			so.processManager.running.record.saveRecord();
			this.addRequestIOVM(so.processManager.running, so.processManager.running.name, so.processManager.running.pc);
			this.addBlockedProcess(so.processManager.running);	
		}

		class RequestIOVirtualMemory {
			public String pid;	//ID de processo
			public String name;
			public int page;

			public RequestIOVirtualMemory(String pid, String name, int page) {
				this.pid = pid;
				this.name = name;
				this.page = page;
			}
		}
	}

	public class VirtualMemoryWorker implements Runnable{
		public VirtualMemoryManager.RequestIOVirtualMemory currentRequest  = null;
		
		public VirtualMemoryWorker(){
			this.currentRequest = null;
		}

		@Override
		public void run() {
			while(true){
				if(so.vmManager.requests.size() > 0 && currentRequest == null){
					currentRequest = so.vmManager.requests.poll();
					processRequest();
				}
			}
		}

		public void processRequest(){
			int frameAddress = memoryMananger.alocByPageFault(this.currentRequest.name, this.currentRequest.page);
			if(frameAddress != -1){
				//Adiciona a página ao processo
				so.processManager.getProcessById(this.currentRequest.pid).pages.set(this.currentRequest.page, frameAddress);
			}
		}

		public void onCreateInterrupt(){
			hw.cpu.irpt = Interrupts.intIORequestVMIsReady;
		}

	    /**
		* Limpa variaveis de controle da requisição atual para tratar a próxima
		*/
		public void onEndRequest(){
			this.currentRequest  = null;
	   	}
	}


	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private HW hw; // referencia ao hw se tiver que setar algo

		public SysCallHandling(HW _hw) {
			hw = _hw;
		}

		public void stop() { // apenas avisa - todas interrupcoes neste momento finalizam o programa
			System.out.println("                                               SYSCALL STOP");
		}

		public void handle() {
			//Define o tipo de operação conforme o vaor do registrador
			String typeIO = (hw.cpu.reg[8] == 1) ? "IN" : "OUT";

			// salva o estado do processo
			so.processManager.running.record.saveRecord();

			System.out.println("Processo " + so.processManager.running.id + " fazendo requisição de IO: " + typeIO);
			// Cria o request para o pedido de IO
			so.ioManager.addRequestIO(so.processManager.running, typeIO, hw.cpu.reg[9]);

			// Coloca o processo na fila de bloqueados por IO
			so.ioManager.addBlockedProcess(so.processManager.running);	
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	// carga na memória
	public class Utilities {
		private HW hw;
		private Semaphore readyToExecute;

		public Utilities(HW _hw, Semaphore _readyToExecute) {
			hw = _hw;
			readyToExecute = _readyToExecute;
		}
		
		private void loadProgram(Word[] p) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = 0; i < p.length; i++) {
				m[i].opc = p[i].opc;
				m[i].ra = p[i].ra;
				m[i].rb = p[i].rb;
				m[i].p = p[i].p;
			}
		}

		

		// dump da memória
		public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.ra);
			System.out.print(", ");
			System.out.print(w.rb);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println("  ] ");
		}

		public void dump(int ini, int fim) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}

		// private void loadAndExec(Word[] p) {
		// 	loadProgram(p); // carga do programa na memoria
		// 	System.out.println("---------------------------------- programa carregado na memoria");
		// 	dump(0, p.length); // dump da memoria nestas posicoes
		// 	hw.cpu.setContext(0); // seta pc para endereço 0 - ponto de entrada dos programas
		// 	System.out.println("---------------------------------- inicia execucao ");
		// 	hw.cpu.run(); // cpu roda programa ate parar
		// 	System.out.println("---------------------------------- memoria após execucao ");
		// 	dump(0, p.length); // dump da memoria com resultado
		// }

		//Deletar o processo, temos que verificar se está rodando ou não
		public void rm(String id){
			if(so.processManager.dieProcess(id)){
				System.out.println("Processo removido");
				return;
			}

			System.out.println("Processo não encontrado");
		}

		//Lista todos os processos
		public void ps(){
			System.out.println("=================================================================");
			for(Sistema.ProcessManager.ProcessControlBlock pcb : so.processManager.allProcesses){
				System.out.println(pcb.id + "\t->\t" + pcb.name + "\t" + pcb.status );
			}
			System.out.println("=================================================================");
		}

		public void dump(String id){
			Sistema.ProcessManager.ProcessControlBlock pcb;
			pcb = so.processManager.getProcessById(id);

			if(pcb != null){
				System.out.println("PCB:");
				String pcbDump = pcb.toString();
				System.out.println(pcbDump);
				System.out.println("Memory:");
				String dumpMem = "";
				for(int i  = 0 ; i < pcb.pages.size() ; i++){
					int frameIndex = pcb.pages.get(i) * so.tamPage;

					for(int j = frameIndex ; j <  (frameIndex + so.tamPage); j++){
						dumpMem += "["+  j  + "]" + " -> " + hw.mem.pos[j].toString() + "\n";
					}	
				}
				System.out.println(dumpMem);
				return;
			}

			System.out.println("Processo não encontrado");
		}

		public void dumpM(int start, int end){
			if(start > end){
				System.out.println("Parâmetros inválidos");
				return;
			}

			for(int i = start; i < end ; i++){
				System.out.println("["+  i  + "] -> " + hw.mem.pos[i].toString());
			}
		}

		public void newProcess(String name){
			for(Program programAux :  progs.progs){
				if (programAux.name.equals(name)) {
					String idProcess = so.processManager.createProgram(programAux, null);
					if(idProcess != ""){
						System.out.println("Processo criado -> ID: " + idProcess);
						return;
					}
				}	
			}

			System.out.println("Erro ao criar processo");	
		}

		public void exec(String idProcess){
			ProcessManager.ProcessControlBlock pcb = so.processManager.getProcessById(idProcess);
			if(pcb == null){
				System.out.println("Processo não enctrado");
				return;
			}
			
			System.out.println("Process is running");
			so.scheduler.runningNext(pcb);
			hw.cpu.run(false);
		}

		public void tradeOn(){
			hw.cpu.debug = true;
		}

		public void tradeOff(){
			hw.cpu.debug = false;	
		}

		public void execAll(){
			// Informa que todos os processos da fila de prontos estão rodando
			System.out.println("Executando todos os processos");

			// Libera o escalonador para rodar todos os processos da fila de prontos
			readyToExecute.release();
		}

		public void in(String pId, String value){
			so.console.inputCommand(Integer.parseInt(pId), Integer.parseInt(value));
		}
	}

	public class SO {
		public InterruptHandling ih;
		public SysCallHandling sc;
		public Utilities utils;
		public int tamPage;
		public ProcessManager processManager;
		public Scheduler scheduler;
		public IOManager ioManager;
		public Console console;
		public VirtualMemoryManager vmManager;
		public VirtualMemoryWorker vmWorker;
		private Semaphore readyQueueControl;

		public SO(HW hw, int tamPage, Semaphore _readyToExecute, Semaphore _finishedCPU, Semaphore _readyCPU) {
			ih = new InterruptHandling(hw); // rotinas de tratamento de int
			sc = new SysCallHandling(hw); // chamadas de sistema
			hw.cpu.setAddressOfHandlers(ih, sc);
			utils = new Utilities(hw, _readyToExecute);
			this.tamPage = tamPage;
			this.processManager =  new ProcessManager();
			this.scheduler = new Scheduler(1, readyToExecute, readyCPU, finishedCPU);
			this.ioManager = new IOManager();
			this.console = new Console();
			this.readyQueueControl = new Semaphore(1);
			this.vmManager = new VirtualMemoryManager();
			this.vmWorker = new VirtualMemoryWorker();
		}
	}
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	
	public HW hw;
	public SO so;
	public Programs progs;
	public MemoryMananger memoryMananger;
	public Semaphore readyCPU, finishedCPU, readyToExecute;

	public Sistema(int tamMem, int tamPage) {
		readyCPU = new Semaphore(0);
		finishedCPU = new Semaphore(0);
		readyToExecute = new Semaphore(0);

		hw = new HW(tamMem, readyCPU, finishedCPU); // memoria do HW tem tamMem palavras
		so = new SO(hw, tamPage, readyToExecute, finishedCPU, readyCPU); // cria sistema operacional
		memoryMananger = new MemoryMananger(); // cria gerenciador de memória
		hw.cpu.setUtilities(so.utils); // permite cpu fazer dump de memoria ao avancar
		progs = new Programs();
	}

	public class MemoryMananger{
		public int numFrames;                   //número  de frames 
		public boolean[] frameMemoryBlockUsed;  //Array que controla o uso de cada frame (By: Pedro - Pensei em criar para não ter que sempre percorrer toda a memória para encontrar um frame vago) 
		
		public MemoryMananger(){
			this.numFrames = (hw.mem.tamMem / so.tamPage);          //cáculo para pegar o número de frames
			this.frameMemoryBlockUsed = new boolean[this.numFrames];   //nosso array de frames usados com o tamanho de frames.	
		}

		/**
		 * Método responsável por alocar somente uma página de um programa em memória
		 * @param words array de palavras de um programa
		 * @return mapeamento das páginas de um programa contendo somente uma página
		 */
		public ArrayList<Integer> alocFirstPage(Word[] words){
			Word[] page = new Word[so.tamPage];


			for(int i = 0 ; i < so.tamPage && words.length > i ; i++){
				page[i] = words[i];
			}

			ArrayList<Integer> frames = getFrames(1); 

			if (frames != null){
				setWordsInFrame(frames.get(0), page);
				return frames;
			}
		
			return null;
		}

		/**
		 * Método responsável por alocar uma página específica em memória, com base no nome do programa
		 * @param nameProgram nome do programa a ser buscada a página
		 * @param page número da página a ser buscada
		 * @return mapeamento do endereço físico do frame alocado
		 */
		public int alocByPageFault(String nameProgram, int page){
			if(hasFreeFrames()){
				System.out.println("Não há frames disponíveis para alocar as páginas do programa.");
				return -1;
			}
			
			Word[] words = getWordsByProgram(nameProgram);
			this.getPageByIndex(words, page);
			
			ArrayList<Integer> frames = getFrames(1); 
			if (frames != null){
				this.setWordsInFrame(frames.get(0), words);
				return frames.get(0);
			}
		
			return -1;
		}

	
		/**
		 * Método que busca todas as words de uma página específica de um programa, e popula a lista
		 * @param words lista de words da página a ser populada
		 * @param index índice da página a ser buscada
		 */
		public void getPageByIndex(Word[] words, int index){
			int indexWord = index * so.tamPage;

			for(int i = 0 ; i < so.tamPage ; i++){
				words[i] = hw.mem.pos[indexWord + i];
			}
		}

		/**
		 * Método que busca todas as palavras de um programa com base no nome
		 * @param name nome do programa
		 * @return
		 */
		public Word[] getWordsByProgram(String name){
			for(Program programAux :  progs.progs){
				if (programAux.name.equals(name)) {
					return programAux.image;
				}	
			}
			return null;		
		}

		/**
		 * Método responsável por alocar um array de palavras em frames livre de memória
		 * @param words array de palavras de um programa
		 * @return mapeamento das páginas do programa com seus respectivos frames
		 */
		public ArrayList<Integer> alocProg(Word[] words){
			// Pega a quantidade de páginas necessárias para alocar em frames
			ArrayList<Word[]> pages = getPages(words);

			// Pega os frames que estão livres
			ArrayList<Integer> frames = getFrames(pages.size());

			// Resposta com o mapeamento das páginas com os frames
			ArrayList<Integer> mapFrames = new ArrayList<>();

			// Valida se temos frames livres
			if (frames != null){
				// Percorre cada pagina do programa
				for (int i = 0; i < pages.size(); i++){
					// Aloca as palavras da pagina no frame livre
					this.setWordsInFrame(frames.get(i), pages.get(i));
					mapFrames.add(frames.get(i));
				}
			} else {
				System.out.println("Não há frames disponíveis para alocar as páginas do programa.");
			}

			// Array com o mapeamento dos frames
			return mapFrames;
		}

		/**
		 * Método responsável desalocar o espaço reservado por um programa
		 * @param mapFrames um ArrayList indicando o endereço lógico dos frames
		 */
		public void dealLocProg(ArrayList<Integer> mapFrames){
			//Percorre o map de frames
			for(int i = 0 ; i < mapFrames.size() ; i++){
				//Pega o endereço físico do frame
				int indexWord = mapFrames.get(i) * so.tamPage;

				//Percorre todos os endereços reservados do frame a partiri do seu início
				for(int j = indexWord ; j < (indexWord + so.tamPage) ; j++){
					hw.mem.pos[j] = new Word(Opcode.___, -1, -1, -1); //desaloca os espaços do frame	
				}

				//Coloca a posição do frame como livre
				this.frameMemoryBlockUsed[mapFrames.get(i)] = false;
			}
		}

		/**
		 * Método responsável por retornar a quantidade de páginas com suas palavras
		 * @param words array de palavras que serão divididas em páginas
		 * @return lista de páginas com palavras
		 */
		private ArrayList<Word[]> getPages(Word[] words){
			// Pega tamanho do programa que solicitou lugar na memória
			int tamProg = words.length;

			// Calcula quantos frames serão necessários
			int totalPages = new BigDecimal(tamProg)
				.divide(new BigDecimal(so.tamPage), 0, RoundingMode.CEILING)
				.round(new MathContext(0, RoundingMode.UP))
				.intValue();

			// Array com as paginas do programa
			ArrayList<Word[]> pages = new ArrayList<>();

			// Percorre o total de paginas do programa
			for (int i = 0; i < totalPages ; i++){
				// Aloca a posicao inicial da pagina
				int indexWord = i * so.tamPage;
				Word[] pageSlice = new Word[so.tamPage];

				// A partir do comeco da pagina, percorre cada palavra
				for (int j = 0 ; j < so.tamPage; j++){
					// Se a posicao na palavra nao for nula, aloca a palavra no array
					if ((indexWord < words.length) && (words[indexWord] != null)){
						pageSlice[j] = words[indexWord];
						indexWord++;
					} else{
						System.out.println("Não há mais palavras a serem alocadas na página.");
						break;
					}
				}
				// Coloca a pagina criada no array de paginas
				pages.add(pageSlice);
			}

			// Retorna o array de paginas
			return pages;
		}

		/**
		 * Método responsável por pegar uma quantidade requisitada de frames livres em memória
		 * @param pages quantidade de frames solicitados
		 * @return array com o mapeamento de frames livres
		 */
		private ArrayList<Integer> getFrames(int pages){
			// Conta a quantidade de páginas na response
			int countPages = 0;
			// Responsável por informar quais frames estão livres
			ArrayList<Integer> frames = new ArrayList<>();

			// Percorre o array de status dos frames
			for (int i = 0; i < this.frameMemoryBlockUsed.length; i++) {
				// Valida se temos frames disponíveis para o programa
				if (countPages == pages){
					return frames;
				}

				// Adiciona o index do frame na resposta
				if (this.frameMemoryBlockUsed[i] == false){
					this.frameMemoryBlockUsed[i] = true;
					frames.add(i);
					countPages++;
				}
			}
			// Caso não encontre frames suficientes, retorna nulo 
			return null;
		}

		/**
		 * Método responsável por alocar as palavras no frame em memória
		 * @param indexFrame index do frame livre
		 * @param words array de palavras que será alocado
		 */
		private void setWordsInFrame(int indexFrame, Word[] words){
			// Pega o endereço inicial do frame em memória
			int memPos = indexFrame * so.tamPage;

			// Percorre o array de palavras e aloca no frame
			for (int i = 0; i < words.length; i++){
				if(words[i] != null)
					hw.mem.pos[memPos + i] = words[i];
			}
		}


		private boolean hasFreeFrames(){
			for (int i = 0; i < this.frameMemoryBlockUsed.length; i++) {
				if (this.frameMemoryBlockUsed[i] == false){
					return true;
				}
			}
			return false;
		}
	}

	public class ProcessManager{
		public Queue<ProcessControlBlock> readyQueue;		//FILA com todos os processos prontos
		private List<ProcessControlBlock> allProcesses;     //Lista com todos os processos
		public ProcessControlBlock running;                 //Ponteiro para o processo que está rodando (importante para o SO)

		public ProcessManager(){
			this.readyQueue = new LinkedList<>();
			this.allProcesses = new LinkedList<>();
			running = null;
		}

		/**
		 * Cria um programa alocando sua memória e criando toda sua estrutura
		 * @param prog - Programa a ser criado
		 * @param parent - Processo criador do programa (pode ser nulo)
		 * @return booleano de confirmação
		 */
		public String createProgram(Program prog, ProcessControlBlock parent){
			ProcessControlBlock pcb = new ProcessControlBlock(prog.name, parent);
			ArrayList<Integer> pages = memoryMananger.alocProg(prog.image);   //Criação da lista de páginas do processo

			//Validação se há memória suficiente
			if(pages == null){
				return "";
			}

			pcb.setPages(pages);                    //seta as páginas para o processo
			pcb.setStatus(ProcessStatus.PRONTO);	//seta o estado do processo
			this.allProcesses.add(pcb);             //adiciona o processo na lista de todos os processos
			
			try {
				so.readyQueueControl.acquire(); 
				this.readyQueue.add(pcb);               //adiciona na lista de prontos
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				so.readyQueueControl.release();            //libera o semáforo para o escalonador
			}
			return pcb.id;
		}

		/**
		 * Destori todo o registro do processo em memória
		 * @param id - id do processo
		 * 
		 */
		public boolean dieProcess(String id){
			ProcessControlBlock pcb = this.getProcessById(id);  //busca o processo na lsita de todos

			if(pcb != null){
				memoryMananger.dealLocProg(pcb.pages);           //desaloca a memória
				allProcesses.removeIf(pcbAux -> pcb.id == pcbAux.id);   //remove de todos
				
				try {
					so.readyQueueControl.acquire(); 
					readyQueue.removeIf(pcbAux -> pcb.id == pcbAux.id);	 //remove dos prontos, caso esteja
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					so.readyQueueControl.release(); 
				} 
			} else {
				return false;
			}
			
			
			return true;
		}

		public boolean readyQueueIsEmpty(){
			return this.readyQueue.isEmpty();
		}

		/**
		 * Traduz um endereço lógico para físico com base na posição lógica (considera o processo rodando pois trabalha com suas páginas)
		 * @param logicalPosition endereço lógico
		 * @return endereço físico da posição
		 */
		public int translateAddress(int logicalPosition){
			try{
				int page = (int) Math.floor(logicalPosition / so.tamPage);

				int pageAdress = this.running.pages.get(page) * so.tamPage;
				int offset = logicalPosition - (page * so.tamPage);

				return pageAdress + offset;
			} catch(Exception e){
				System.out.println(e);
			}
			
			return -1;
		}

		/**
		 * Recupera o pcb com base no id
		 * @param id  - id do processo
		 * @return
		 */
		public ProcessControlBlock getProcessById(String id) {
			// Busca o PCB com o ID especificado
			for(ProcessControlBlock pcbAux : this.allProcesses){
				if(pcbAux.id.equals(id.trim())){
					return pcbAux;
				}
			}
			return null;
		}

		
		//Classe aninhada para o PCB
		public class ProcessControlBlock{
			public String id;                      //id do processo
			public int pc;                      //program counter
			public String name;                 //nome do programa
			public ProcessControlBlock parent;  //processo pai (se houver)
			public ArrayList<Integer> pages;    //mapa de frames (paginas)
			public ProcessStatus status;        //status atual do processo
			public Record record;               //Último estado da cpu
			public long startTime;            //tempo em que entrou na cpu

			public ProcessControlBlock(String name, ProcessControlBlock parent){
				this.parent = parent;
				this.name = name;
				this.status = ProcessStatus.NOVO;
				this.id = this.generateUniqueRandomId();
				this.pc = 0;
				this.record = new Record();			
			}

			/**
			 * Seta as páginas do processo
			 * @param pages mapa com paginas e frames do processo
			 */
			public void setPages(ArrayList<Integer> pages) {
				this.pages = pages;
			}

			/**
			 * Seta o novo status do processo
			 * @param status
			 */
			public void setStatus(ProcessStatus status) {
				this.status = status;
			}


			public void setStartTime(long startTime) {
				this.startTime = startTime;
			}

			/**
			 * Gera um id único para o processo
			 * @return inteiro para ser usado como id do processo
			 */
			private String generateUniqueRandomId() {
				Random random = new Random();
				Integer newId;
				boolean isValid = true;
				do{
					newId = random.nextInt(Integer.MAX_VALUE);;
					// Continua gerando até encontrar um ID que não foi usado

					for (ProcessControlBlock pcb : allProcesses) {
						if (pcb.id.toString() == newId.toString()) {
							isValid = false;; // Encontra o maior ID existente
						}
					}
				}while(!isValid);
				
				return newId.toString(); // Retorna o novo ID único
			}

			/**
			 * Incremenda o contador lógico do processo
			 * @return inteiro para ser usado como id do processo
			 */
			public void incLogicProgramCounter(){
				this.pc++;
			}


			public String toString(){
				String result = this.id + " ->\t " + this.name + "\t" + this.pages + "\t" + this.status + "\t" + this.startTime;
				return result;
			}

			/**
			 * Seta o contador lógico do processo para um valor específico
			 * @param pc
			 */
			public void setPc(int pc) {
				this.pc = pc;
			}

			//Classe aninhado para salvar o estado do processo na cpu
			public class Record{
				public int[] reg;   //Cópia dos registradores da cpu
				public int pc;      //Cópia para pc da CPU
				private Word ir;    //Cópia IR CPU

				public Record(){
					this.pc = 0;
					this.reg = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
					this.ir = null;
				}

				/**
				 * Salva o estado da cpu na ultima execução do processo
				 */
				public void saveRecord(){
					this.pc = hw.cpu.pc;
					for(int i = 0 ; i < hw.cpu.reg.length ; i++){
						this.reg[i] = hw.cpu.reg[i];
					}
					this.ir = hw.cpu.ir;
				}

				/**
				 * Carrega a cpu com o último estado do processo
				 */
				public void loadRecord(){
					hw.cpu.pc = this.pc;
					for(int i = 0 ; i < hw.cpu.reg.length ; i++){
						hw.cpu.reg[i]  = this.reg[i];
					}
					hw.cpu.ir = this.ir;	
				}
			}
		}	
	}


	public class Scheduler implements Runnable{
		private long maxTime;
		private Semaphore readyToExecute, readyCPU, finishedCPU;
			
		public Scheduler(long maxTime, Semaphore _readyToExecute, Semaphore _readyCPU, Semaphore _finishedCPU) {
			this.maxTime = maxTime;
			this.readyToExecute = _readyToExecute;
			this.readyCPU = _readyCPU;
			this.finishedCPU = _finishedCPU;
		}

		/**
		 * Coloca o próximo processo pra rodar (nesse caso eu simplesmente coloquei o proximo da fila e não fiz nada com o antigo), temos que tratar, se o antigo não acabou, vai pro final da fila, se acabou, temos que dar um die
		 * @param id  - id do processo
		 * @return
		 */
		public void runningNext(ProcessManager.ProcessControlBlock pcb) {
			try{
				so.readyQueueControl.acquire();
				if(so.processManager.running != null){
					// salva o estado do atual (DENTRO DO PCB)
					so.processManager.running.record.saveRecord();
					// atualiza o status do processo (PRONTO)
					so.processManager.running.setStatus(ProcessStatus.PRONTO);
					// Coloca o processo que estava RUNNING na fila de prontos
					so.processManager.readyQueue.add(so.processManager.running);
				}
				
				if(pcb != null){
					int i = 0;
					for (ProcessManager.ProcessControlBlock process : so.processManager.readyQueue) {
						if (pcb.id.equals(process.id)) {
							so.processManager.running =	pcb;
							so.processManager.readyQueue.remove(i);
						}
						i++;
					}
				} else {
					so.processManager.running = so.processManager.readyQueue.poll();
				}
				
				// Carrega seu último estado
				so.processManager.running.record.loadRecord();
				// setar o startTime curretnTime
				so.processManager.running.startTime = System.currentTimeMillis(); 
				// seta o status como rodando
				so.processManager.running.setStatus(ProcessStatus.EXECUTANDO);	
				// setContext cpu
				hw.cpu.setContext(so.processManager.running.pc); 
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				so.readyQueueControl.release(); 
			}
		}

		public boolean checkTimerInterrupt(){
			if(so.processManager.running != null){
				if(((System.currentTimeMillis() - so.processManager.running.startTime) * 100) >= maxTime){
					return true;
				}
			}
			
			return false;
		}

		@Override
		public void run(){
			while(true){
				try {
					// Espera o sinal para liberar a CPU rodar (semáforo)
					readyToExecute.acquire();

				   // Enquanto tiver processos na fila de prontos
					while(!so.processManager.readyQueueIsEmpty() || so.processManager.running != null || so.ioManager.blockedQueue.size() > 0 || so.vmManager.blockedQueueByVM.size() >0){
						// Retira o processo da CPU e executa o próximo da fila de prontos
						if (!so.processManager.readyQueueIsEmpty()){
							so.scheduler.runningNext(null);
						} 
						
						if (so.processManager.running != null){
							System.out.println(so.processManager.running.id + " is running");
						}
						
						// Libera a CPU para rodar
						readyCPU.release();
						// Espera o sinal de finalização da CPU
						finishedCPU.acquire();		
					}
					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}




	public enum ProcessStatus {
		NOVO,          // O processo foi criado, mas ainda não está em execução.
		PRONTO,        // O processo está pronto para ser executado e aguardando a CPU.
		EXECUTANDO,    // O processo está atualmente em execução.
		BLOQUEADO,     // O processo está bloqueado, aguardando algum evento (ex.: I/O).
		FINALIZADO;    // O processo terminou sua execução.
	}

	public void run() {
		// for (Program p : progs.progs) {
		// 	System.out.println("################################################### ");
		// 	System.out.println("################################################### ");
		// 	System.out.println("-------------------- " + p.name);
		// 	so.utils.loadAndExec(p.image);
		// }
		Thread cpu = new Thread(hw.cpu);
		Thread scheduler = new Thread(so.scheduler);
		Thread user = new Thread(new UserShell(hw.cpu.u));
		Thread console = new Thread(so.console);
		Thread vmWorder = new Thread(so.vmWorker);

		cpu.start();
		scheduler.start();
		user.start();
		console.start();
		vmWorder.start();
		
	
		// so.utils.loadAndExec(progs.retrieveProgram("fatorial"));
		// fibonacci10,
		// fibonacci10v2,
		// progMinimo,
		// fatorialWRITE, // saida
		// fibonacciREAD, // entrada
		// PB
		// PC, // bubble sort
	}
	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema(1024, 8);
		s.run();
	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Program {
		public String name;
		public Word[] image;

		public Program(String n, Word[] i) {
			name = n;
			image = i;
		}
	}

	public class Programs {

		public Word[] retrieveProgram(String pname) {
			for (Program p : progs) {
				if (p != null & p.name == pname)
					return p.image;
			}
			return null;
		}

		public Program[] progs = {
				new Program("fatorial",
						new Word[] {
								// este fatorial so aceita valores positivos. nao pode ser zero
								// linha coment
								new Word(Opcode.LDI, 0, -1, 7), // 0 r0 é valor a calcular fatorial
								new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
								new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 o decremento
								new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao 8 para fim do programa
								new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
								new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0 (r1 acumula o produto por cada termo)
								new Word(Opcode.SUB, 0, 6, -1), // 6 r0 = r0 - r6 (r6=1) decrementa r0 para proximo
																// termo
								new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
								new Word(Opcode.STD, 1, -1, 13), // 8 coloca valor de r1 na posição 10
								new Word(Opcode.LDI, 8, -1, 2),
								new Word(Opcode.LDI, 9, -1, 13), // endereco com valor a escrever
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1), // 9 stop
								new Word(Opcode.DATA, -1, -1, -1) // 10 ao final o valor está na posição 10 da memória
						}),

				new Program("fatorialV2",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula para STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 18),
								new Word(Opcode.LDI, 8, -1, 2), // escrita
								new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1), // POS 17
								new Word(Opcode.DATA, -1, -1, -1),  // POS 18
								new Word(Opcode.DATA, -1, -1, -1) } // POS 18
				),

				new Program("progMinimo",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 999),
								new Word(Opcode.STD, 0, -1, 8),
								new Word(Opcode.STD, 0, -1, 9),
								new Word(Opcode.STD, 0, -1, 10),
								new Word(Opcode.STD, 0, -1, 11),
								new Word(Opcode.STD, 0, -1, 12),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // 7
								new Word(Opcode.DATA, -1, -1, -1), // 8
								new Word(Opcode.DATA, -1, -1, -1), // 9
								new Word(Opcode.DATA, -1, -1, -1), // 10
								new Word(Opcode.DATA, -1, -1, -1), // 11
								new Word(Opcode.DATA, -1, -1, -1), // 12
								new Word(Opcode.DATA, -1, -1, -1) // 13
						}),

				new Program("fibonacci10",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),

				new Program("fibonacci10v2",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.MOVE, 3, 1, -1),
								new Word(Opcode.MOVE, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),
				new Program("fibonacciREAD",
						new Word[] {
								// mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 8, -1, 1), // leitura
								new Word(Opcode.LDI, 9, -1, 55), // endereco a guardar o tamanho da serie de fib a gerar
																	// - pode ser de 1 a 20
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.LDD, 7, -1, 55),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 7, -1),
								new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
								new Word(Opcode.LDI, 1, -1, -1), // caso negativo
								new Word(Opcode.STD, 1, -1, 41),
								new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
								new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
								new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de
																	// fibonacci gerada
								new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.ADDI, 3, -1, 1),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 42),
								new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.LDI, 0, -1, 43),
								new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
								new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
								new Word(Opcode.ADD, 5, 7, -1),
								new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
								new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
								new Word(Opcode.STOP, -1, -1, -1), // POS 36
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 41
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("PB",
						new Word[] {
								// dado um inteiro em alguma posição de memória,
								// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
								// número na saída
								new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 15),
								new Word(Opcode.STOP, -1, -1, -1), // POS 14
								new Word(Opcode.DATA, -1, -1, -1) // POS 15
						}),
				new Program("PC",
						new Word[] {
								// Para um N definido (10 por exemplo)
								// o programa ordena um vetor de N números em alguma posição de memória;
								// ordena usando bubble sort
								// loop ate que não swap nada
								// passando pelos N valores
								// faz swap de vizinhos se da esquerda maior que da direita
								new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
								new Word(Opcode.LDI, 6, -1, 5), // aux N
								new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
								new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
								new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
								new Word(Opcode.STD, 0, -1, 46),
								new Word(Opcode.LDI, 0, -1, 3),
								new Word(Opcode.STD, 0, -1, 47),
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 48),
								new Word(Opcode.LDI, 0, -1, 1),
								new Word(Opcode.STD, 0, -1, 49),
								new Word(Opcode.LDI, 0, -1, 2),
								new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
								new Word(Opcode.STD, 3, -1, 99),
								new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
								new Word(Opcode.STD, 3, -1, 98),
								new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
								new Word(Opcode.STD, 3, -1, 97),
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
								new Word(Opcode.STD, 3, -1, 96),
								new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
								new Word(Opcode.ADD, 6, 7, -1),
								new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
								new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para
																	// interomper o loop de vez do programa
								new Word(Opcode.LDX, 0, 5, -1), // r0 e ra pegando valores das posições da memoria POS
																// 26
								new Word(Opcode.LDX, 1, 4, -1),
								new Word(Opcode.LDI, 2, -1, 0),
								new Word(Opcode.ADD, 2, 0, -1),
								new Word(Opcode.SUB, 2, 1, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
								new Word(Opcode.STX, 5, 1, -1),
								new Word(Opcode.SUBI, 4, -1, 1),
								new Word(Opcode.STX, 4, 0, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
								new Word(Opcode.ADDI, 5, -1, 1),
								new Word(Opcode.SUBI, 7, -1, 1),
								new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
								new Word(Opcode.ADD, 4, 5, -1),
								new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
								new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
								new Word(Opcode.STOP, -1, -1, -1), // POS 45
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						})
		};
	}
}