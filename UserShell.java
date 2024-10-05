import java.util.Scanner;

public class UserShell implements Runnable{
    private Sistema.Utilities utilities;

    public UserShell(Sistema.Utilities utilities) {
        this.utilities = utilities;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("so_project_BGGP$ ");
            String command = scanner.nextLine().trim();
            interpretCommand(command);
        }
    }

    private void interpretCommand(String command) {

        String[] parts = getPartsOfString(command);

        if(parts == null){
            System.out.println("Comando inválido.");
        }

        String mainCommand = parts[0];

        switch (mainCommand.toLowerCase()) {
            case "new":
                if(parts[1] == "" || parts[1] == null){
                    System.out.println("Parâmetros inválidos.");
                    return;
                }
                this.utilities.newProcess(parts[1]);
                break;

            case "rm":
                if(parts[1] == "" || parts[1] == null){
                    System.out.println("Parâmetros inválidos.");
                    return;
                }
                
                this.utilities.rm(parts[1]);
                break;
            case "ps":
                this.utilities.ps();
                break;
            case "dump":
                if(parts[1] == "" || parts[1] == null){
                    System.out.println("Parâmetros inválidos.");
                    return;
                }
                this.utilities.dump(parts[1]);
                break;

            case "dumpm":
                if(parts[1] == "" || parts[1] == null || parts[2] == "" || parts[2] == null){
                    System.out.println("Parâmetros inválidos.");
                    return;
                }
                this.utilities.dumpM(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                break;

            case "exec":
                if(parts[1] == "" || parts[1] == null){
                    System.out.println("Parâmetros inválidos.");
                    return;
                }
                this.utilities.exec(parts[1]);
                break;

            case "traceon":
                this.utilities.tradeOn();
                break;
            
            case "traceoff":
                this.utilities.tradeOff();
                break;

            case "execall":
                this.utilities.execAll();
                break;

            case "exit":
                System.out.println("Encerrando...");
                System.exit(0);
                break;
            // Adicione mais comandos conforme necessário.
            default:
                System.out.println("Comando não reconhecido.");
        }
    }

    private String[] getPartsOfString(String input){
        String[] parts = input.trim().split(" ");
        String[] result = new String[3];
        int count = 0;

        for(int i = 0 ; i < parts.length ; i++){
            if(parts[i] != ""){
                if((count + 1) >= 4){
                    return null;
                }
                result[count] = parts[i].trim();
                count++;
            }
        }

        return result;
    }
}